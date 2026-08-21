package de.tododl.desktop.panels

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import de.tododl.desktop.ui.TodoListPanelScreen

object TodoListPanelPlugin : PanelPlugin {
    override val id = "PANEL_TODOLIST"
    override val displayName = "Todoliste"
    override val description = "Aufgaben mit Person, Dringlichkeit, Termin, Frist und Unteraufgaben"
    override val icon: ImageVector = Icons.Default.Checklist
    override val badgeLabel = "TODOLISTE"

    @Composable
    override fun accentColor(): Color = MaterialTheme.colorScheme.primary

    @Composable
    override fun Content(panelId: String) {
        TodoListPanelScreen(panelId = panelId)
    }
}
