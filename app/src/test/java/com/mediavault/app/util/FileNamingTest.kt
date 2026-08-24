package com.mediavault.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamingTest {

    @Test
    fun `sanitizeFileName strips path separators, preventing path traversal`() {
        assertEquals(".._.._etc_passwd", sanitizeFileName("../../etc/passwd"))
        assertEquals("a_b", sanitizeFileName("a\\b"))
    }

    @Test
    fun `sanitizeFileName strips other filesystem-unsafe characters`() {
        assertEquals("My_Video_", sanitizeFileName("My:Video?"))
    }

    @Test
    fun `sanitizeFileName trims and falls back to a generic name when blank`() {
        assertEquals("file", sanitizeFileName("   "))
        assertEquals("Title", sanitizeFileName("  Title  "))
    }

    @Test
    fun `sanitizeFileName truncates very long names`() {
        val result = sanitizeFileName("a".repeat(300))
        assertEquals(150, result.length)
    }

    @Test
    fun `nextAvailableFileName returns the desired name when it's free`() {
        assertEquals("video.mp4", nextAvailableFileName("video.mp4", existingNames = emptySet()))
    }

    @Test
    fun `nextAvailableFileName appends a counter before the extension on collision`() {
        assertEquals("video (1).mp4", nextAvailableFileName("video.mp4", existingNames = setOf("video.mp4")))
    }

    @Test
    fun `nextAvailableFileName finds the first free counter`() {
        val existing = setOf("video.mp4", "video (1).mp4", "video (2).mp4")
        assertEquals("video (3).mp4", nextAvailableFileName("video.mp4", existing))
    }

    @Test
    fun `nextAvailableFileName handles a name with no extension`() {
        assertEquals("video (1)", nextAvailableFileName("video", existingNames = setOf("video")))
    }
}
