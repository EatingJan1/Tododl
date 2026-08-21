package de.tododl.desktop.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Zwei Darstellungen für denselben Roh-Markdown-Text:
 *
 *  - SOURCE: IDE-artiges Bearbeiten des rohen Markdown mit Syntax-Highlighting
 *    (Farben je Element) + Autovervollständigung (siehe MarkdownAutocomplete).
 *  - RICH: WYSIWYG für Laien - Steuerzeichen (**, *, `, #, - [ ], ...) werden
 *    NICHT gelöscht (der Roh-Text bleibt die einzige Quelle der Wahrheit,
 *    wichtig für Server-Sync), sondern nur unsichtbar gemacht (durch
 *    Leerzeichen ersetzt, exakt gleiche Zeichenanzahl -> Cursor-Mapping ist
 *    dadurch eine reine Identität, keine komplexe OffsetMapping nötig).
 *    Bearbeiten geschieht dort ausschließlich über das Formatierungsmenü
 *    (Toolbar), nicht durch Tippen von Markdown-Syntax.
 */
enum class MarkdownMode { SOURCE, RICH }

private val ColorHeading = Color(0xFF1A73E8)
private val ColorSyntaxMarker = Color(0xFF9AA0A6)
private val ColorQuote = Color(0xFF5F6368)
private val ColorCodeBg = Color(0x22808080)
private val ColorMention = Color(0xFF6C63FF)
private val ColorMentionBg = Color(0x1A6C63FF)
private val ColorLink = Color(0xFF1A73E8)
private val ColorDivider = Color(0xFFBDBDBD)

private val fenceRegex = Regex("(?s)```.*?```")
private val headingRegex = Regex("""(?m)^(#{1,3})[ \t]""")
private val quoteRegex = Regex("""(?m)^>[ \t]""")
private val checkboxRegex = Regex("""(?m)^-[ \t]\[( |x|X)]\s""")
private val bulletRegex = Regex("""(?m)^[-*][ \t](?!\[)""")
private val dividerRegex = Regex("""(?m)^-{3,}$""")
private val boldRegex = Regex("""\*\*[^*\n]+\*\*""")
private val italicRegex = Regex("""(?<!\*)\*[^*\n]+\*(?!\*)""")
private val strikeRegex = Regex("""~~[^~\n]+~~""")
private val codeRegex = Regex("""`[^`\n]+`""")
private val linkRegex = Regex("""\[[^\]\n]+]\([^)\n]+\)""")
private val mentionRegex = Regex("""@[a-zA-Z0-9_.\-]+""")

/** Liefert die Namen aller @mentions im Text, die tatsächlich in [validUsers] vorkommen. */
fun extractValidMentions(text: String, validUsers: Set<String>): List<String> =
    mentionRegex.findAll(text)
        .map { it.value.removePrefix("@") }
        .filter { it in validUsers }
        .distinct()
        .toList()

/**
 * Baut die eingefärbte/maskierte Darstellung. Gibt IMMER einen String gleicher
 * Länge wie [text] zurück (Zeichen werden ersetzt, nie gelöscht/eingefügt),
 * damit Cursor-Positionen 1:1 erhalten bleiben.
 */
fun annotateMarkdown(text: String, mode: MarkdownMode, validUsers: Set<String>): AnnotatedString {
    val display = StringBuilder(text)
    val spans = mutableListOf<AnnotatedString.Range<SpanStyle>>()

    fun mask(range: IntRange) {
        if (mode == MarkdownMode.RICH) {
            for (i in range) display.setCharAt(i, ' ')
        }
    }

    fun style(range: IntRange, style: SpanStyle) {
        if (range.isEmpty()) return
        spans += AnnotatedString.Range(style, range.first, range.last + 1)
    }

    // Fence-Blöcke zuerst markieren, damit innerer Inhalt nicht nochmal von
    // Bold/Italic/etc. erwischt wird.
    val fenced = mutableListOf<IntRange>()
    for (m in fenceRegex.findAll(text)) {
        fenced += m.range
        style(m.range, SpanStyle(fontFamily = FontFamily.Monospace, background = ColorCodeBg))
        mask(m.range.first..(m.range.first + 2))
        mask((m.range.last - 2)..m.range.last)
    }
    fun insideFence(idx: Int) = fenced.any { idx in it }

    for (m in headingRegex.findAll(text)) {
        if (insideFence(m.range.first)) continue
        val level = m.groupValues[1].length
        val lineEnd = text.indexOf('\n', m.range.last).let { if (it == -1) text.length else it }
        style(m.range.first until lineEnd, SpanStyle(fontWeight = FontWeight.Bold, color = ColorHeading, fontSize = (23 - level * 2).sp))
        style(m.range, SpanStyle(color = ColorSyntaxMarker))
        mask(m.range)
    }

    for (m in quoteRegex.findAll(text)) {
        if (insideFence(m.range.first)) continue
        val lineEnd = text.indexOf('\n', m.range.last).let { if (it == -1) text.length else it }
        style(m.range.first until lineEnd, SpanStyle(fontStyle = FontStyle.Italic, color = ColorQuote))
        mask(m.range)
    }

    for (m in checkboxRegex.findAll(text)) {
        if (insideFence(m.range.first)) continue
        val checked = m.groupValues[1].equals("x", ignoreCase = true)
        if (mode == MarkdownMode.RICH) {
            display.setCharAt(m.range.first, if (checked) '☑' else '☐')
            for (i in (m.range.first + 1)..m.range.last) display.setCharAt(i, ' ')
        } else {
            style(m.range, SpanStyle(color = ColorSyntaxMarker, fontWeight = FontWeight.Bold))
        }
        if (checked) {
            val lineEnd = text.indexOf('\n', m.range.last).let { if (it == -1) text.length else it }
            style((m.range.last + 1) until lineEnd, SpanStyle(textDecoration = TextDecoration.LineThrough, color = ColorSyntaxMarker))
        }
    }

    for (m in bulletRegex.findAll(text)) {
        if (insideFence(m.range.first)) continue
        if (mode == MarkdownMode.RICH) {
            display.setCharAt(m.range.first, '•')
            for (i in (m.range.first + 1)..m.range.last) display.setCharAt(i, ' ')
        } else {
            style(m.range, SpanStyle(color = ColorSyntaxMarker, fontWeight = FontWeight.Bold))
        }
    }

    for (m in dividerRegex.findAll(text)) {
        if (insideFence(m.range.first)) continue
        style(m.range, SpanStyle(color = ColorDivider))
        if (mode == MarkdownMode.RICH) {
            for (i in m.range) display.setCharAt(i, '─')
        }
    }

    for (m in boldRegex.findAll(text)) {
        if (insideFence(m.range.first)) continue
        style(m.range, SpanStyle(fontWeight = FontWeight.Bold))
        style(m.range.first until (m.range.first + 2), SpanStyle(color = ColorSyntaxMarker))
        style((m.range.last - 1)..m.range.last, SpanStyle(color = ColorSyntaxMarker))
        mask(m.range.first until (m.range.first + 2))
        mask((m.range.last - 1)..m.range.last)
    }

    for (m in italicRegex.findAll(text)) {
        if (insideFence(m.range.first)) continue
        style(m.range, SpanStyle(fontStyle = FontStyle.Italic))
        mask(m.range.first..m.range.first)
        mask(m.range.last..m.range.last)
    }

    for (m in strikeRegex.findAll(text)) {
        if (insideFence(m.range.first)) continue
        style(m.range, SpanStyle(textDecoration = TextDecoration.LineThrough))
        mask(m.range.first until (m.range.first + 2))
        mask((m.range.last - 1)..m.range.last)
    }

    for (m in codeRegex.findAll(text)) {
        if (insideFence(m.range.first)) continue
        style(m.range, SpanStyle(fontFamily = FontFamily.Monospace, background = ColorCodeBg))
        mask(m.range.first..m.range.first)
        mask(m.range.last..m.range.last)
    }

    for (m in linkRegex.findAll(text)) {
        if (insideFence(m.range.first)) continue
        val labelEnd = text.indexOf(']', m.range.first)
        if (labelEnd == -1) continue
        style((m.range.first + 1) until labelEnd, SpanStyle(color = ColorLink, textDecoration = TextDecoration.Underline))
        mask(m.range.first..m.range.first)
        mask(labelEnd..m.range.last)
    }

    // Bug-Fix: Nur @mentions, die wirklich existierende Nutzer sind, werden als
    // Link hervorgehoben. Beliebiger "@irgendwas"-Text bleibt normaler Text.
    for (m in mentionRegex.findAll(text)) {
        if (insideFence(m.range.first)) continue
        val username = m.value.removePrefix("@")
        if (username in validUsers) {
            style(m.range, SpanStyle(color = ColorMention, fontWeight = FontWeight.SemiBold, background = ColorMentionBg))
        }
    }

    return AnnotatedString(display.toString(), spans)
}

/** VisualTransformation für ein OutlinedTextField - Länge bleibt exakt erhalten, daher OffsetMapping.Identity. */
class MarkdownVisualTransformation(
    private val mode: MarkdownMode,
    private val validUsers: Set<String>
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(annotateMarkdown(text.text, mode, validUsers), OffsetMapping.Identity)
}
