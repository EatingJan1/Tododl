package de.tododl.desktop.ui.markdown

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Ein Eintrag im Slash-Befehl-Autocomplete des IDE-Modus (SOURCE). */
data class SlashCommand(
    val keyword: String,
    val label: String,
    val icon: ImageVector,
    val insert: (TextFieldValue) -> TextFieldValue
)

/** Eine Formatierungs-Aktion der Toolbar im Laien-Modus (RICH). Wirkt auf die Selektion. */
data class FormatAction(
    val label: String,
    val icon: ImageVector,
    val apply: (TextFieldValue) -> TextFieldValue
)

private fun wrapSelection(value: TextFieldValue, prefix: String, suffix: String = prefix, placeholder: String): TextFieldValue {
    val sel = value.selection
    val text = value.text
    val selectedText = if (!sel.collapsed) text.substring(sel.min, sel.max) else placeholder
    val before = text.substring(0, sel.min)
    val after = text.substring(sel.max)
    val newText = "$before$prefix$selectedText$suffix$after"
    val cursorStart = before.length + prefix.length
    return TextFieldValue(newText, TextRange(cursorStart, cursorStart + selectedText.length))
}

/** Setzt/entfernt einen Zeilen-Präfix (z.B. "# ", "> ", "- ") für alle Zeilen der Selektion. */
private fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val sel = value.selection
    var lineStart = text.lastIndexOf('\n', (sel.min - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
    if (sel.min == 0) lineStart = 0
    val lineEndSearch = text.indexOf('\n', sel.max)
    val lineEnd = if (lineEndSearch == -1) text.length else lineEndSearch

    val block = text.substring(lineStart, lineEnd)
    val lines = block.split("\n")
    val alreadyPrefixed = lines.all { it.startsWith(prefix) || it.isBlank() }
    val newLines = lines.map { line ->
        when {
            line.isBlank() -> line
            alreadyPrefixed -> line.removePrefix(prefix)
            else -> prefix + line
        }
    }
    val newBlock = newLines.joinToString("\n")
    val newText = text.substring(0, lineStart) + newBlock + text.substring(lineEnd)
    val delta = newBlock.length - block.length
    return TextFieldValue(newText, TextRange(sel.min + (if (alreadyPrefixed) -prefix.length else prefix.length), (sel.max + delta).coerceAtLeast(0)))
}

/** Formatierungsmenü für den Laien-Modus (RICH). Bearbeiten von Formatierung ist NUR hierüber möglich, nicht durch Tippen von Markdown-Zeichen. */
val markdownFormatActions: List<FormatAction> = listOf(
    FormatAction("Fett", Icons.Default.FormatBold) { wrapSelection(it, "**", placeholder = "fett") },
    FormatAction("Kursiv", Icons.Default.FormatItalic) { wrapSelection(it, "*", placeholder = "kursiv") },
    FormatAction("Durchgestrichen", Icons.Default.FormatStrikethrough) { wrapSelection(it, "~~", placeholder = "durchgestrichen") },
    FormatAction("Code", Icons.Default.Code) { wrapSelection(it, "`", placeholder = "code") },
    FormatAction("Überschrift", Icons.Default.Title) { toggleLinePrefix(it, "# ") },
    FormatAction("Zitat", Icons.Default.FormatQuote) { toggleLinePrefix(it, "> ") },
    FormatAction("Aufzählung", Icons.Default.FormatListBulleted) { toggleLinePrefix(it, "- ") },
    FormatAction("Checkliste", Icons.Default.Checklist) { toggleLinePrefix(it, "- [ ] ") },
    FormatAction("Link", Icons.Default.Link) { wrapSelection(it, "[", "](url)", placeholder = "linktext") },
    FormatAction("Trennlinie", Icons.Default.HorizontalRule) { value ->
        val text = value.text
        val sel = value.selection
        val insertion = "\n---\n"
        val newText = text.substring(0, sel.min) + insertion + text.substring(sel.max)
        val cursor = sel.min + insertion.length
        TextFieldValue(newText, TextRange(cursor))
    }
)

/** Slash-Befehle im IDE-Modus (SOURCE) - "/" tippen zeigt dieses Menü als Autocomplete. */
val markdownSlashCommands: List<SlashCommand> = listOf(
    SlashCommand("h1", "Überschrift 1", Icons.Default.Title) { insertAtLineStart(it, "# ") },
    SlashCommand("h2", "Überschrift 2", Icons.Default.Title) { insertAtLineStart(it, "## ") },
    SlashCommand("h3", "Überschrift 3", Icons.Default.Title) { insertAtLineStart(it, "### ") },
    SlashCommand("liste", "Aufzählung", Icons.Default.FormatListBulleted) { insertAtLineStart(it, "- ") },
    SlashCommand("check", "Checkliste", Icons.Default.Checklist) { insertAtLineStart(it, "- [ ] ") },
    SlashCommand("zitat", "Zitat", Icons.Default.FormatQuote) { insertAtLineStart(it, "> ") },
    SlashCommand("code", "Codeblock", Icons.Default.Code) { insertBlock(it, "```\n", "\n```") },
    SlashCommand("tabelle", "Tabelle (2x2)", Icons.Default.TableChart) {
        insertBlock(it, "", "| Spalte 1 | Spalte 2 |\n| --- | --- |\n| a | b |")
    },
    SlashCommand("trennlinie", "Trennlinie", Icons.Default.HorizontalRule) { insertBlock(it, "", "\n---\n") },
    SlashCommand("link", "Link", Icons.Default.Link) { wrapSelection(it, "[", "](url)", placeholder = "linktext") }
)

/** Entfernt das gerade getippte "/befehl"-Fragment am Cursor und ersetzt die Zeile durch das gewählte Element. */
private fun insertAtLineStart(value: TextFieldValue, marker: String): TextFieldValue {
    val cursor = value.selection.end
    val text = value.text
    var start = cursor
    while (start > 0 && text[start - 1] != '\n') start--
    val newText = text.substring(0, start) + marker + text.substring(cursor)
    val newCursor = start + marker.length
    return TextFieldValue(newText, TextRange(newCursor))
}

private fun insertBlock(value: TextFieldValue, before: String, after: String): TextFieldValue {
    val cursor = value.selection.end
    val text = value.text
    val newText = text.substring(0, cursor) + before + after + text.substring(cursor)
    val newCursor = cursor + before.length
    return TextFieldValue(newText, TextRange(newCursor))
}

/** Erkennt "/befehl" direkt vor dem Cursor (analog zur bestehenden @mention-Erkennung). */
fun findSlashQuery(value: TextFieldValue): Pair<IntRange, String>? {
    val cursor = value.selection.end
    val text = value.text
    var start = cursor - 1
    while (start >= 0 && text[start] != '/' && !text[start].isWhitespace()) start--
    if (start < 0 || text[start] != '/') return null
    if (start > 0 && !text[start - 1].isWhitespace() && text[start - 1] != '\n') return null
    return (start..cursor) to text.substring(start + 1, cursor)
}

/** Ersetzt "/query" am Cursor durch das eingefügte Element eines Slash-Befehls. */
fun applySlashCommand(value: TextFieldValue, range: IntRange, command: SlashCommand): TextFieldValue {
    val text = value.text
    val withoutSlash = TextFieldValue(
        text.substring(0, range.first) + text.substring(range.last.coerceAtMost(text.length)),
        TextRange(range.first)
    )
    return command.insert(withoutSlash)
}
