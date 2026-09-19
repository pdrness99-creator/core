package org.simpmusic.aiservice

import com.maxrave.domain.data.model.metadata.Line
import com.maxrave.domain.data.model.metadata.Lyrics

class AiClient {
    private var aiService: AiService? = null
    var host = AIHost.GEMINI
        set(value) {
            field = value
            rebuildAiService()
        }
    var apiKey: String? = null
        set(value) {
            field = value
            rebuildAiService()
        }
    var customModelId: String? = null
        set(value) {
            field = value
            rebuildAiService()
        }
    var customBaseUrl: String? = null
        set(value) {
            field = value
            rebuildAiService()
        }
    var customHeaders: Map<String, String>? = null
        set(value) {
            field = value
            rebuildAiService()
        }

    private fun rebuildAiService() {
        aiService =
            if (apiKey != null) {
                AiService(
                    aiHost = host,
                    apiKey = apiKey!!,
                    customModelId = customModelId,
                    customBaseUrl = customBaseUrl,
                    customHeaders = customHeaders,
                )
            } else {
                null
            }
    }

    suspend fun translateLyrics(
        inputLyrics: Lyrics,
        targetLanguage: String,
    ): Result<Lyrics> =
        runCatching {
            val result =
                aiService?.translateLyrics(inputLyrics, targetLanguage)
                    ?: throw IllegalStateException("AI service is not initialized. Please set host and apiKey.")

            val originalLines =
                inputLyrics.lines
                    ?: throw IllegalStateException("Original lyrics lines are missing.")
            val translatedLines =
                result.lines
                    ?: throw IllegalStateException("Translated lyrics lines are missing.")

            if (originalLines.size != translatedLines.size) {
                throw IllegalStateException(
                    "Translation returned an unexpected number of lyric lines.",
                )
            }

            if (!hasMeaningfulTranslation(originalLines, translatedLines)) {
                throw IllegalStateException(
                    "Translation failed or returned lyrics identical to the source.",
                )
            }

            result
        }
}

internal fun hasMeaningfulTranslation(
    originalLines: List<Line>,
    translatedLines: List<Line>,
): Boolean =
    originalLines.indices.any { index ->
        val original = originalLines[index].words.trim()
        val translated = translatedLines[index].words.trim()
        original.isNotEmpty() &&
            original != "♫" &&
            original != translated
    }
