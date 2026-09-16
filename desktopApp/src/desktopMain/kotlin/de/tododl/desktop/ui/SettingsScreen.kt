package de.tododl.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.ui.theme.LocalNotionColors

/** Ein Reiter im Einstellungen-Fenster. Neuen Bereich hinzufügen = hier eintragen + Content-Fall unten ergänzen. */
private enum class SettingsTab(val label: String, val icon: ImageVector) {
    ALLGEMEIN("Allgemein", Icons.Default.Tune),
    VERBINDUNGEN("Verbindungen", Icons.Default.Cable),
    SERVER("Meine Server", Icons.Default.Dns)
}

/**
 * Einstellungen-Fenster, analog zu macOS-Settings: Sidebar links, Inhalt rechts.
 * Wird als eigenes Fenster geöffnet (siehe Main.kt, Menü "Tododl > Einstellungen…",
 * Tastenkürzel Cmd+,) statt als Teil der normalen Haupt-Navigation - Einstellungen
 * sollen jederzeit erreichbar sein, unabhängig davon, welcher Bereich/Projekt
 * gerade offen ist.
 */
@Composable
fun SettingsScreen(isDarkMode: Boolean, onToggleDarkMode: () -> Unit) {
    var selectedTab by remember { mutableStateOf(SettingsTab.VERBINDUNGEN) }
    val notionColors = LocalNotionColors.current

    Row(Modifier.fillMaxSize()) {
        // Sidebar
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            Text(
                "Einstellungen",
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = notionColors.textSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            SettingsTab.entries.forEach { tab ->
                SettingsTabRow(tab = tab, selected = tab == selectedTab, onClick = { selectedTab = tab })
            }
        }

        VerticalDivider()

        // Content
        Box(Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            when (selectedTab) {
                SettingsTab.ALLGEMEIN -> AllgemeinSettingsContent(isDarkMode = isDarkMode, onToggleDarkMode = onToggleDarkMode)
                SettingsTab.VERBINDUNGEN -> ConnectorsScreen()
                SettingsTab.SERVER -> ServerListScreen(onOpenGruppen = { _, _ -> })
            }
        }
    }
}

@Composable
private fun SettingsTabRow(tab: SettingsTab, selected: Boolean, onClick: () -> Unit) {
    val notionColors = LocalNotionColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            tab.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else notionColors.textSecondary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            tab.label,
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AllgemeinSettingsContent(isDarkMode: Boolean, onToggleDarkMode: () -> Unit) {
    Column {
        Text("Allgemein", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dunkles Design", fontSize = 14.sp)
                Text("Wechselt zwischen hellem und dunklem Farbschema.", fontSize = 12.sp, color = LocalNotionColors.current.textSecondary)
            }
            Switch(checked = isDarkMode, onCheckedChange = { onToggleDarkMode() })
        }
    }
}
