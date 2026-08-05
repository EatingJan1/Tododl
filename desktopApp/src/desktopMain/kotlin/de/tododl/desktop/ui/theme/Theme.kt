package de.tododl.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TododlColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6C63FF),
    secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
    background = androidx.compose.ui.graphics.Color(0xFF121212),
    surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E)
)

@Composable
fun TododlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TododlColors,
        content = content
    )
}
