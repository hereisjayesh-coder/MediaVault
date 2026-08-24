package com.mediavault.app.library

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [renameFile] is the one piece of [AndroidLibraryRepository]'s rename/delete logic that needs
 * neither a Room DAO nor an Android `Context` — real [File] I/O behaves identically on the JVM
 * test runner and on-device, so this exercises the actual filesystem rename against a real temp
 * directory rather than a fake. The Context-touching parts of rename/delete/export (content://
 * fallback, FileProvider, Room wiring) are covered by real-device testing and code review, same
 * as the rest of this project's Android-coupled engine code.
 */
class LibraryRepositoryTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("mediavault-library-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun fileNamed(name: String): File = File(dir, name).apply { writeText("data") }

    @Test
    fun `renaming a file keeps its extension and applies sanitized name`() {
        val file = fileNamed("Original.mp4")

        val renamed = renameFile(file, "My New Title")

        assertEquals("My New Title.mp4", renamed?.name)
        assertTrue(renamed!!.exists())
        assertTrue(!file.exists())
    }

    @Test
    fun `renaming to a name that collides with another file gets a counter suffix`() {
        val file = fileNamed("A.mp4")
        fileNamed("B.mp4") // already occupies the desired target name

        val renamed = renameFile(file, "B")

        assertEquals("B (1).mp4", renamed?.name)
    }

    @Test
    fun `renaming strips path-traversal characters from the new title`() {
        val file = fileNamed("A.mp4")

        val renamed = renameFile(file, "../../etc/passwd")

        assertEquals(dir, renamed?.parentFile)
        assertTrue(renamed!!.name.none { it == '/' || it == '\\' })
    }

    @Test
    fun `renaming a file that no longer exists fails cleanly instead of throwing`() {
        val file = File(dir, "gone.mp4") // never created

        val renamed = renameFile(file, "New Name")

        assertNull(renamed)
    }
}
