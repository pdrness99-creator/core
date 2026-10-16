package org.simpmusic.aiservice

import com.aallam.openai.api.chat.ChatCompletionRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricsTranslationRequestTest {
    private val inputJson = """{"0":"Hello","2":"Goodbye"}"""

    private fun request(host: AIHost): JsonObject {
        val service = AiService(
            aiHost = host,
            apiKey = "unused-test-key",
            customModelId = "test-model",
            customBaseUrl = "https://api.deepseek.com/v1/",
        )
        return Json.encodeToJsonElement(
            ChatCompletionRequest.serializer(),
            service.createTranslationRequest(inputJson, "zh-CN"),
        ).jsonObject
    }

    @Test
    fun customProviderRequestUsesDeepSeekCompatibleJsonMode() {
        val format = request(AIHost.CUSTOM_OPENAI)
            .getValue("response_format").jsonObject

        assertEquals("json_object", format.getValue("type").jsonPrimitive.content)
        assertTrue(format["json_schema"] == null)
    }

    @Test
    fun builtInProvidersRetainTheirSchema() {
        for (host in listOf(AIHost.OPENAI, AIHost.GEMINI)) {
            val format = request(host).getValue("response_format").jsonObject

            assertEquals("json_schema", format.getValue("type").jsonPrimitive.content)
            val schema = format.getValue("json_schema").jsonObject
                .getValue("schema").jsonObject
            assertTrue(
                "translations" in schema.getValue("properties").jsonObject,
            )
        }
    }

    @Test
    fun requestPreservesModelTargetLanguageAndLyrics() {
        val body = request(AIHost.CUSTOM_OPENAI)

        assertEquals("test-model", body.getValue("model").jsonPrimitive.content)

        val messages = body.getValue("messages").jsonArray
        assertTrue(
            messages.first().jsonObject.getValue("content")
                .jsonPrimitive.content.contains("JSON"),
        )

        val userParts = messages.last().jsonObject.getValue("content")
            .jsonArray.map {
                it.jsonObject.getValue("text").jsonPrimitive.content
            }

        assertEquals(
            listOf(
                "Target language: zh-CN",
                "Input lyrics: $inputJson",
            ),
            userParts,
        )
    }
}
