package com.mediavault.app.library

import com.mediavault.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaFileClassifierTest {

    @Test
    fun `common video extensions classify as video, case-insensitively`() {
        assertEquals(MediaType.VIDEO, mediaTypeForExtension("mp4"))
        assertEquals(MediaType.VIDEO, mediaTypeForExtension("MKV"))
        assertEquals(MediaType.VIDEO, mediaTypeForExtension("WebM"))
    }

    @Test
    fun `common audio extensions classify as audio, case-insensitively`() {
        assertEquals(MediaType.AUDIO, mediaTypeForExtension("mp3"))
        assertEquals(MediaType.AUDIO, mediaTypeForExtension("M4A"))
        assertEquals(MediaType.AUDIO, mediaTypeForExtension("flac"))
    }

    @Test
    fun `a non-media extension classifies as null, not guessed as either type`() {
        assertNull(mediaTypeForExtension("jpg"))
        assertNull(mediaTypeForExtension("pdf"))
        assertNull(mediaTypeForExtension(""))
    }

    @Test
    fun `extensionOf lowercases and returns only the segment after the final dot`() {
        assertEquals("mp4", extensionOf("My Video.MP4"))
        assertEquals("gz", extensionOf("archive.tar.gz"))
    }

    @Test
    fun `extensionOf is empty for a file with no extension`() {
        assertEquals("", extensionOf("README"))
    }

    @Test
    fun `titleFromFileName strips exactly one trailing extension`() {
        assertEquals("My Video", titleFromFileName("My Video.mp4"))
        assertEquals("archive.tar", titleFromFileName("archive.tar.gz"))
    }

    @Test
    fun `titleFromFileName falls back to the full name when there's nothing to strip`() {
        assertEquals("README", titleFromFileName("README"))
    }
}
