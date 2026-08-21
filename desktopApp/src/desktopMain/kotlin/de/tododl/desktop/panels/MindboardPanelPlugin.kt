package de.tododl.desktop.panels

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import de.tododl.desktop.ui.MindboardPanelScreen
import de.tododl.desktop.ui.theme.LocalNotionColors

object MindboardPanelPlugin : PanelPlugin {
    override val id = "PANEL_MINDBOARD"
    override val displayName = "Mindboard"
    override val description = "Freie Notizen als frei verschiebbare Karten"
    override val icon: ImageVector = Icons.Default.Lightbulb
    override val badgeLabel = "MINDBOARD"

    @Composable
    override fun accentColor(): Color = LocalNotionColors.current.badgeMindboard

    @Composable
    override fun Content(panelId: String) {
        MindboardPanelScreen(panelId = panelId)
    }
}
