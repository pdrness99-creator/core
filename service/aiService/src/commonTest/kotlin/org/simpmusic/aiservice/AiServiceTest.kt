package org.simpmusic.aiservice

import com.maxrave.domain.data.model.metadata.Line
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiServiceTest {
    @Test
    fun extractsJsonFromMarkdownCodeFence() {
        val response = "```json\n{\"translations\":{\"0\":\"你好\"}}\n```"

        assertEquals(
            "{\"translations\":{\"0\":\"你好\"}}",
            extractJsonPayload(response),
        )
    }

    @Test
    fun keepsPlainJsonUntouched() {
        val response = "{\"translations\":{\"0\":\"你好\"}}"

        assertEquals(response, extractJsonPayload(response))
    }

    @Test
    fun acceptsExactTranslationKeySet() {
        validateTranslationMap(
            expectedKeys = setOf("0", "2"),
            translatedMap = mapOf("0" to "你好", "2" to "世界"),
        )
    }

    @Test
    fun rejectsMissingTranslationKeys() {
        assertFailsWith<IllegalStateException> {
            validateTranslationMap(
                expectedKeys = setOf("0", "1"),
                translatedMap = mapOf("0" to "你好"),
            )
        }
    }

    @Test
    fun rejectsUnexpectedTranslationKeys() {
        assertFailsWith<IllegalStateException> {
            validateTranslationMap(
                expectedKeys = setOf("0"),
                translatedMap = mapOf("0" to "你好", "9" to "额外"),
            )
        }
    }

    @Test
    fun rejectsBlankTranslationValues() {
        assertFailsWith<IllegalStateException> {
            validateTranslationMap(
                expectedKeys = setOf("0"),
                translatedMap = mapOf("0" to "   "),
            )
        }
    }

    @Test
    fun detectsMeaningfulTranslationOnlyOnTranslatableLines() {
        val original =
            listOf(
                Line(startTimeMs = 0L, endTimeMs = 1L, words = "", syllables = null),
                Line(startTimeMs = 1L, endTimeMs = 2L, words = "♫", syllables = null),
                Line(startTimeMs = 2L, endTimeMs = 3L, words = "hello", syllables = null),
            )
        val translated =
            listOf(
                original[0],
                original[1],
                Line(startTimeMs = 2L, endTimeMs = 3L, words = "你好", syllables = null),
            )

        assertTrue(hasMeaningfulTranslation(original, translated))
        assertFalse(hasMeaningfulTranslation(original, original))
    }
}
