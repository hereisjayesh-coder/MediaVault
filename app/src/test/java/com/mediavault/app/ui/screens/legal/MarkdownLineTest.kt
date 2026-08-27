package com.mediavault.app.ui.screens.legal

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownLineTest {

    @Test
    fun `classifies a title, heading, subtitle, bullet, and paragraph`() {
        val raw = """
            # Privacy Policy

            _Last updated: 2026-08-24_

            ## What MediaVault does not do

            - No backend server.

            A plain paragraph.
        """.trimIndent()

        val lines = parseSimpleMarkdown(raw)

        assertEquals(
            listOf(
                MarkdownLine.Title("Privacy Policy"),
                MarkdownLine.Subtitle("Last updated: 2026-08-24"),
                MarkdownLine.Heading("What MediaVault does not do"),
                MarkdownLine.Bullet("No backend server."),
                MarkdownLine.Paragraph("A plain paragraph."),
            ),
            lines,
        )
    }

    @Test
    fun `strips markdown links to their plain text`() {
        val lines = parseSimpleMarkdown("See [TERMS.md](TERMS.md) for details.")

        assertEquals(listOf(MarkdownLine.Paragraph("See TERMS.md for details.")), lines)
    }

    @Test
    fun `parses a table row into cells and skips the separator row`() {
        val raw = "| Project | Purpose | License |\n|---|---|---|\n| yt-dlp | Extraction | Unlicense |"

        val lines = parseSimpleMarkdown(raw)

        assertEquals(
            listOf(
                MarkdownLine.TableRow(listOf("Project", "Purpose", "License")),
                MarkdownLine.TableRow(listOf("yt-dlp", "Extraction", "Unlicense")),
            ),
            lines,
        )
    }

    @Test
    fun `blank lines are dropped entirely`() {
        val lines = parseSimpleMarkdown("First.\n\n\nSecond.")

        assertEquals(listOf(MarkdownLine.Paragraph("First."), MarkdownLine.Paragraph("Second.")), lines)
    }
}
