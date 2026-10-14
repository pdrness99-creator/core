package org.simpmusic.aiservice

import com.maxrave.domain.data.model.metadata.Line
import com.maxrave.domain.data.model.metadata.Lyrics
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercises the real SDK and HTTP transport against a local, deterministic provider. */
class AiTranslationTest {
    @Test
    fun sendsJsonObjectModeAndBothPromptInputsAsText() =
        runBlocking {
            FakeProvider().use { provider ->
                provider.client().translateLyrics(lyrics("Morning light"), "zh-CN").getOrThrow()
                val request = provider.request()
                val body = request.body
                assertEquals("POST", request.method)
                assertEquals("/v1/chat/completions", request.path)
                assertEquals("Bearer test-key", request.authorization)
                assertEquals("deepseek-flash", body["model"]?.jsonPrimitive?.content)
                assertEquals("json_object", body["response_format"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
                assertFalse(body["response_format"]!!.jsonObject.containsKey("json_schema"))
                val content = assertIs<JsonPrimitive>(body["messages"]!!.jsonArray.last().jsonObject["content"])
                assertTrue(content.content.contains("Target language: zh-CN"))
                assertTrue(content.content.contains("Morning light"))
            }
        }

    @Test
    fun keepsVersionPathWhenBaseUrlHasNoTrailingSlash() =
        runBlocking {
            FakeProvider().use { provider ->
                provider.client(basePath = "/v1").translateLyrics(lyrics("Morning light"), "zh").getOrThrow()
                assertEquals("/v1/chat/completions", provider.request().path)
            }
        }

    @Test
    fun acceptsFullChatCompletionsUrl() =
        runBlocking {
            FakeProvider().use { provider ->
                provider.client(basePath = "/v1/chat/completions/").translateLyrics(lyrics("Morning light"), "zh").getOrThrow()
                assertEquals("/v1/chat/completions", provider.request().path)
            }
        }

    @Test
    fun acceptsRootBaseUrl() =
        runBlocking {
            FakeProvider().use { provider ->
                provider.client(basePath = "").translateLyrics(lyrics("Morning light"), "zh").getOrThrow()
                assertEquals("/chat/completions", provider.request().path)
            }
        }

    @Test
    fun trimsPastedConfigurationAndPreservesCustomHeaders() =
        runBlocking {
            FakeProvider().use { provider ->
                val client = provider.client().apply {
                    customBaseUrl = "  ${provider.baseUrl}/v1  "
                    customModelId = "  deepseek-flash  "
                    apiKey = "  test-key  "
                    customHeaders = mapOf("X-Test-Provider" to "custom")
                }
                client.translateLyrics(lyrics("Morning light"), "zh").getOrThrow()
                val request = provider.request()
                assertEquals("/v1/chat/completions", request.path)
                assertEquals("deepseek-flash", request.body["model"]?.jsonPrimitive?.content)
                assertEquals("Bearer test-key", request.authorization)
                assertEquals("custom", request.customHeader)
            }
        }

    @Test
    fun doesNotCountBlankLinesOrMusicMarkersAsFailedTranslations() =
        runBlocking {
            FakeProvider("""{"translations":{"1":"晨光","3":"照亮归途"}}""").use { provider ->
                val original = lyrics("", "Morning light", "♫", "Lights the way home", " ")
                val result = provider.client().translateLyrics(original, "zh").getOrThrow()
                val translatedLines = assertNotNull(result.lines)
                assertEquals(listOf("", "晨光", "♫", "照亮归途", " "), translatedLines.map { it.words })
                assertEquals(original.syncType, result.syncType)
                assertNotNull(original.lines).zip(translatedLines).forEach { (before, after) ->
                    assertEquals(before.startTimeMs, after.startTimeMs)
                    assertEquals(before.endTimeMs, after.endTimeMs)
                }
            }
        }

    @Test
    fun rejectsSameLanguageAndUnchangedTranslations() =
        runBlocking {
            for (response in listOf("""{"translations":{}}""", """{"translations":{"0":"Morning light"}}""")) {
                FakeProvider(response).use { provider ->
                    assertTrue(provider.client().translateLyrics(lyrics("Morning light"), "zh").isFailure)
                }
            }
        }

    @Test
    fun rejectsIncompleteOrBlankTranslations() =
        runBlocking {
            for (response in listOf("""{"translations":{"0":"晨光"}}""", """{"translations":{"0":"晨光","1":" "}}""")) {
                FakeProvider(response).use { provider ->
                    assertTrue(provider.client().translateLyrics(lyrics("Morning light", "Lights the way home"), "zh").isFailure)
                }
            }
        }

    @Test
    fun preservesInstrumentalLinesAndClearsTranslatedSyllables() =
        runBlocking {
            FakeProvider("""{"translations":{"0":"晨光","2":"归途"}}""").use { provider ->
                val original = lyrics("Morning light", "♫", "Way home")
                val result = provider.client().translateLyrics(original, "zh").getOrThrow()
                val translatedLines = assertNotNull(result.lines)
                assertEquals(assertNotNull(original.lines)[1], translatedLines[1])
                assertEquals(null, translatedLines[0].syllables)
                assertEquals(null, translatedLines[2].syllables)
            }
        }

    @Test
    fun acceptsFencedJsonAndIgnoresReasoningContent() =
        runBlocking {
            FakeProvider("```json\n{\"translations\":{\"0\":\"晨光\"}}\n```").use { provider ->
                val result = provider.client().translateLyrics(lyrics("Morning light"), "zh").getOrThrow()
                assertEquals("晨光", result.lines!!.single().words)
            }
        }

    private fun lyrics(vararg words: String) =
        Lyrics(
            lines = words.mapIndexed { index, text ->
                Line(
                    startTimeMs = (index * 1000).toString(),
                    endTimeMs = ((index + 1) * 1000).toString(),
                    words = text,
                    syllables = listOf("original timing"),
                )
            },
            syncType = "LINE_SYNCED",
        )

    private data class Request(
        val method: String,
        val path: String,
        val authorization: String?,
        val customHeader: String?,
        val body: JsonObject,
    )

    private class FakeProvider(
        content: String = """{"translations":{"0":"晨光"}}""",
    ) : AutoCloseable {
        private val requests = LinkedBlockingQueue<Request>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val baseUrl = "http://127.0.0.1:${server.address.port}"

        init {
            val response = buildJsonObject {
                put("id", "test-completion")
                put("object", "chat.completion")
                put("created", 1)
                put("model", "deepseek-flash")
                put("choices", buildJsonArray {
                    add(buildJsonObject {
                        put("index", 0)
                        put("finish_reason", "stop")
                        put("message", buildJsonObject {
                            put("role", "assistant")
                            put("content", content)
                            put("reasoning_content", "This field must not become translated lyrics.")
                        })
                    })
                })
            }.toString().toByteArray(Charsets.UTF_8)
            server.createContext("/") { exchange ->
                exchange.use {
                    requests.add(
                        Request(
                            method = exchange.requestMethod,
                            path = exchange.requestURI.path,
                            authorization = exchange.requestHeaders.getFirst("Authorization"),
                            customHeader = exchange.requestHeaders.getFirst("X-Test-Provider"),
                            body = Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject,
                        ),
                    )
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, response.size.toLong())
                    exchange.responseBody.write(response)
                }
            }
            server.start()
        }

        fun client(basePath: String = "/v1/") =
            AiClient().apply {
                host = AIHost.CUSTOM_OPENAI
                apiKey = "test-key"
                customBaseUrl = baseUrl + basePath
                customModelId = "deepseek-flash"
            }

        fun request(): Request = assertNotNull(requests.poll(5, TimeUnit.SECONDS), "No HTTP request received")

        override fun close() = server.stop(0)
    }
}
