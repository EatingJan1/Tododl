package de.tododl.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.state.koinGet
import de.tododl.desktop.ui.components.NotionPageHeader
import de.tododl.desktop.ui.markdown.MarkdownMode
import de.tododl.desktop.ui.markdown.MarkdownVisualTransformation
import de.tododl.desktop.ui.markdown.applySlashCommand
import de.tododl.desktop.ui.markdown.findSlashQuery
import de.tododl.desktop.ui.markdown.markdownFormatActions
import de.tododl.desktop.ui.markdown.markdownSlashCommands
import de.tododl.desktop.ui.theme.LocalNotionColors
import de.tododl.shared.model.MarkdownPage
import de.tododl.shared.remote.SyncManager
import de.tododl.shared.remote.UserDto
import de.tododl.shared.repository.MarkdownPageRepository
import kotlinx.coroutines.launch

@Composable
fun MarkdownPanelScreen(panelId: String) {
    val repo = remember { koinGet<MarkdownPageRepository>() }
    val syncManager = remember { koinGet<SyncManager>() }
    val scope = rememberCoroutineScope()
    val notionColors = LocalNotionColors.current

    // RICH = Ansicht/Laien-Modus (WYSIWYG, Bearbeiten nur über Formatierungsmenü),
    // SOURCE = Bearbeiten/IDE-Modus (roher Markdown-Text, Syntax-Highlighting, Autocomplete).
    var mode by remember { mutableStateOf(MarkdownMode.RICH) }
    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var savedContent by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // Bug-Fix "nur echte User verlinken": Liste gültiger Nutzernamen dieses
    // Servers wird einmal geladen und sowohl fürs Highlighting als auch fürs
    // @mention-Autocomplete verwendet - erfundene/getippte "@irgendwas"-Texte,
    // die zu keinem echten Nutzer gehören, werden NIE als Link dargestellt.
    var validUsernames by remember { mutableStateOf<Set<String>>(emptySet()) }

    var mentionSuggestions by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var mentionRange by remember { mutableStateOf<IntRange?>(null) }
    var slashSuggestions by remember { mutableStateOf(markdownSlashCommands) }
    var slashRange by remember { mutableStateOf<IntRange?>(null) }

    LaunchedEffect(panelId) {
        val page = repo.getPage(panelId)
        val content = page?.content ?: ""
        fieldValue = TextFieldValue(content)
        savedContent = content

        validUsernames = try {
            syncManager.searchUsersForPanel(panelId, "").map { it.username }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    val isDirty = fieldValue.text != savedContent

    fun save() {
        scope.launch {
            isSaving = true
            val content = fieldValue.text
            repo.upsert(MarkdownPage(panelId = panelId, content = content))
            savedContent = content
            try {
                syncManager.pushMarkdownPageIfNeeded(MarkdownPage(panelId = panelId, content = content))
            } catch (_: Exception) {
                // Push fehlgeschlagen (z. B. offline) - lokal ist trotzdem gespeichert.
            }
            isSaving = false
        }
    }

    // @-Mention- und /-Slash-Erkennung, nur im SOURCE-Modus relevant
    // (RICH bearbeitet man ausschließlich über die Toolbar).
    LaunchedEffect(fieldValue.selection, fieldValue.text, mode) {
        if (mode != MarkdownMode.SOURCE) {
            mentionRange = null; mentionSuggestions = emptyList()
            slashRange = null
            return@LaunchedEffect
        }

        val cursor = fieldValue.selection.end
        val text = fieldValue.text
        var start = cursor - 1
        while (start >= 0 && text[start] != '@' && !text[start].isWhitespace()) start--

        if (start >= 0 && text[start] == '@' && cursor <= text.length) {
            val query = text.substring(start + 1, cursor)
            mentionRange = start..cursor
            mentionSuggestions = try {
                syncManager.searchUsersForPanel(panelId, query)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            mentionRange = null
            mentionSuggestions = emptyList()
        }

        val slash = findSlashQuery(fieldValue)
        if (slash != null) {
            val (range, query) = slash
            slashRange = range
            slashSuggestions = markdownSlashCommands.filter {
                it.keyword.startsWith(query, ignoreCase = true) || query.isBlank()
            }
        } else {
            slashRange = null
        }
    }

    fun insertMention(username: String) {
        val range = mentionRange ?: return
        val text = fieldValue.text
        val before = text.substring(0, range.first)
        val after = text.substring(range.last.coerceAtMost(text.length))
        val newText = "$before@$username $after"
        val newCursor = before.length + username.length + 2
        fieldValue = TextFieldValue(newText, TextRange(newCursor))
        mentionRange = null
        mentionSuggestions = emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        NotionPageHeader(
            icon = Icons.Default.Description,
            iconColor = MaterialTheme.colorScheme.primary,
            title = "Markdown-Seite",
            subtitle = when {
                isSaving -> "Speichert…"
                isDirty -> "Ungespeicherte Änderungen"
                else -> "Alles gespeichert"
            },
            badgeLabel = "PANEL"
        )

        Row(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SegmentedButtonRow(mode, onModeChange = { mode = it })
            Spacer(Modifier.weight(1f))
            Button(onClick = { save() }, enabled = isDirty && !isSaving) {
                Text(if (isSaving) "Speichert…" else "Speichern")
            }
        }

        // Formatierungsmenü: im Ansichts-/Laien-Modus ist Formatierung AUSSCHLIESSLICH
        // hierüber möglich - kein Tippen von Markdown-Syntax nötig oder vorgesehen.
        if (mode == MarkdownMode.RICH) {
            FormatToolbar(
                onAction = { action -> fieldValue = action.apply(fieldValue) }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = {
                        Text(
                            if (mode == MarkdownMode.SOURCE)
                                "Roher Markdown-Text - # Überschrift, **fett**, - Liste, @name, / für Befehle …"
                            else
                                "Schreib hier deinen Text - Formatierung über das Menü oben …"
                        )
                    },
                    textStyle = if (mode == MarkdownMode.SOURCE)
                        androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    else
                        androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                    visualTransformation = MarkdownVisualTransformation(mode, validUsernames)
                )

                if (mode == MarkdownMode.SOURCE) {
                    if (mentionRange != null && mentionSuggestions.isNotEmpty()) {
                        MentionSuggestionPopup(mentionSuggestions, onSelect = { insertMention(it.username) })
                    } else if (slashRange != null && slashSuggestions.isNotEmpty()) {
                        SlashSuggestionPopup(
                            suggestions = slashSuggestions,
                            onSelect = { cmd ->
                                slashRange?.let { fieldValue = applySlashCommand(fieldValue, it, cmd) }
                                slashRange = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedButtonRow(mode: MarkdownMode, onModeChange: (MarkdownMode) -> Unit) {
    val notionColors = LocalNotionColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, notionColors.border, RoundedCornerShape(8.dp))
    ) {
        SegmentButton("Ansicht (einfach)", Icons.Default.Person, mode == MarkdownMode.RICH) { onModeChange(MarkdownMode.RICH) }
        SegmentButton("Bearbeiten (IDE)", Icons.Default.Code, mode == MarkdownMode.SOURCE) { onModeChange(MarkdownMode.SOURCE) }
    }
}

@Composable
private fun SegmentButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val notionColors = LocalNotionColors.current
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else notionColors.textSecondary
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else notionColors.textSecondary,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
        )
    }
}

/** Formatierungsmenü (Toolbar) für den Laien-Modus - einziger Weg, dort Formatierung zu ändern. */
@Composable
private fun FormatToolbar(onAction: (de.tododl.desktop.ui.markdown.FormatAction) -> Unit) {
    val notionColors = LocalNotionColors.current
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(markdownFormatActions) { action ->
            OutlinedButton(
                onClick = { onAction(action) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, notionColors.border)
            ) {
                Icon(action.icon, contentDescription = action.label, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(action.label, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MentionSuggestionPopup(suggestions: List<UserDto>, onSelect: (UserDto) -> Unit) {
    val notionColors = LocalNotionColors.current
    Surface(
        modifier = Modifier.widthIn(min = 200.dp, max = 280.dp).padding(top = 4.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, notionColors.border),
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            suggestions.take(6).forEach { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(user) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("@${user.username}", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(user.name, fontSize = 12.sp, color = notionColors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun SlashSuggestionPopup(suggestions: List<de.tododl.desktop.ui.markdown.SlashCommand>, onSelect: (de.tododl.desktop.ui.markdown.SlashCommand) -> Unit) {
    val notionColors = LocalNotionColors.current
    Surface(
        modifier = Modifier.widthIn(min = 220.dp, max = 300.dp).padding(top = 4.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, notionColors.border),
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            suggestions.take(8).forEach { cmd ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(cmd) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(cmd.icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = notionColors.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("/${cmd.keyword}", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(cmd.label, fontSize = 12.sp, color = notionColors.textSecondary)
                }
            }
        }
    }
}
