package de.tododl.desktop.panels

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import de.tododl.desktop.ui.MarkdownPanelScreen

object MarkdownPanelPlugin : PanelPlugin {
    override val id = "PANEL_MARKDOWN"
    override val displayName = "Markdown-Seite"
    override val description = "Freier Text mit Markdown-Formatierung und @-Mentions"
    override val icon: ImageVector = Icons.Default.Description
    override val badgeLabel = "MARKDOWN"

    @Composable
    override fun accentColor(): Color = MaterialTheme.colorScheme.primary

    @Composable
    override fun Content(panelId: String) {
        MarkdownPanelScreen(panelId = panelId)
    }
}
