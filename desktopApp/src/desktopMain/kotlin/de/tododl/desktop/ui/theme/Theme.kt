package de.tododl.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val NotionCharcoal = Color(0xFF37352F)
val NotionLightBg = Color(0xFFFBFBFA)
val NotionLightSidebar = Color(0xFFF7F7F5)
val NotionLightSurface = Color(0xFFFFFFFF)
val NotionLightBorder = Color(0xFFE9E9E8)
val NotionLightTextSecondary = Color(0xFF787774)

val NotionDarkBg = Color(0xFF191919)
val NotionDarkSidebar = Color(0xFF202020)
val NotionDarkSurface = Color(0xFF252525)
val NotionDarkBorder = Color(0xFF2E2E2E)
val NotionDarkTextPrimary = Color(0xFFE3E3E3)
val NotionDarkTextSecondary = Color(0xFF9B9B9B)

val NotionAccentBlue = Color(0xFF2383E2)
val NotionAccentPurple = Color(0xFF9065B0)
val NotionAccentGreen = Color(0xFF446B54)
val NotionAccentOrange = Color(0xFFD9730D)
val NotionAccentRed = Color(0xFFD44C47)

data class NotionExtendedColors(
    val sidebarBackground: Color,
    val border: Color,
    val textSecondary: Color,
    val hoverHighlight: Color,
    val badgeFolder: Color,
    val badgeTodoList: Color,
    val badgeMindboard: Color
)

val LocalNotionColors = staticCompositionLocalOf {
    NotionExtendedColors(
        sidebarBackground = NotionDarkSidebar,
        border = NotionDarkBorder,
        textSecondary = NotionDarkTextSecondary,
        hoverHighlight = Color(0x1FFFFFFF),
        badgeFolder = Color(0xFF352F22),
        badgeTodoList = Color(0xFF1E3247),
        badgeMindboard = Color(0xFF3B2544)
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = NotionAccentBlue,
    secondary = NotionAccentPurple,
    background = NotionDarkBg,
    surface = NotionDarkSurface,
    onBackground = NotionDarkTextPrimary,
    onSurface = NotionDarkTextPrimary,
    outline = NotionDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = NotionAccentBlue,
    secondary = NotionAccentPurple,
    background = NotionLightBg,
    surface = NotionLightSurface,
    onBackground = NotionCharcoal,
    onSurface = NotionCharcoal,
    outline = NotionLightBorder
)

@Composable
fun TododlTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        NotionExtendedColors(
            sidebarBackground = NotionDarkSidebar,
            border = NotionDarkBorder,
            textSecondary = NotionDarkTextSecondary,
            hoverHighlight = Color(0x1FFFFFFF),
            badgeFolder = Color(0xFF352F22),
            badgeTodoList = Color(0xFF1E3247),
            badgeMindboard = Color(0xFF3B2544)
        )
    } else {
        NotionExtendedColors(
            sidebarBackground = NotionLightSidebar,
            border = NotionLightBorder,
            textSecondary = NotionLightTextSecondary,
            hoverHighlight = Color(0x0F000000),
            badgeFolder = Color(0xFFFBF3DB),
            badgeTodoList = Color(0xFFE8F3F7),
            badgeMindboard = Color(0xFFF3E8F7)
        )
    }

    CompositionLocalProvider(LocalNotionColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
