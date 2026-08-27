package com.mediavault.app.ui.screens.legal

/**
 * A minimal, pure line-classifier for the small set of Markdown constructs this app's own
 * legal documents actually use (headings, bullets, simple 3-column tables, italic subtitles,
 * plain paragraphs) — deliberately not a general Markdown parser/library (no unnecessary
 * dependency for three static documents). Pure Kotlin, no Android types, so it's plain-JUnit
 * testable.
 */
sealed class MarkdownLine {
    data class Title(val text: String) : MarkdownLine()
    data class Heading(val text: String) : MarkdownLine()
    data class Subtitle(val text: String) : MarkdownLine()
    data class Bullet(val text: String) : MarkdownLine()
    data class TableRow(val cells: List<String>) : MarkdownLine()
    data class Paragraph(val text: String) : MarkdownLine()
}

private val linkPattern = Regex("""\[([^]]+)]\([^)]+\)""")
private val tableSeparatorPattern = Regex("""^\|[-\s|]+\|$""")

private fun cleanInline(text: String): String =
    linkPattern.replace(text) { it.groupValues[1] }
        .replace("`", "")
        .replace("**", "")

fun parseSimpleMarkdown(raw: String): List<MarkdownLine> =
    raw.lines().mapNotNull { rawLine ->
        val line = rawLine.trimEnd()
        val trimmedStart = line.trimStart()
        when {
            line.isBlank() -> null
            line.startsWith("# ") -> MarkdownLine.Title(cleanInline(line.removePrefix("# ")))
            line.startsWith("## ") -> MarkdownLine.Heading(cleanInline(line.removePrefix("## ")))
            line.startsWith("_") && line.endsWith("_") && line.length > 1 ->
                MarkdownLine.Subtitle(cleanInline(line.trim('_')))
            trimmedStart.startsWith("- ") -> MarkdownLine.Bullet(cleanInline(trimmedStart.removePrefix("- ")))
            tableSeparatorPattern.matches(line) -> null
            line.startsWith("|") -> MarkdownLine.TableRow(line.trim('|').split("|").map { cleanInline(it.trim()) })
            else -> MarkdownLine.Paragraph(cleanInline(line))
        }
    }
