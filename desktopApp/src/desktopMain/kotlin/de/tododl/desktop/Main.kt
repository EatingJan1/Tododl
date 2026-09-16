package de.tododl.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import de.tododl.desktop.connectors.desktopConnectorsModule
import de.tododl.desktop.navigation.TododlApp
import de.tododl.desktop.ui.SettingsScreen
import de.tododl.desktop.ui.theme.TododlTheme
import de.tododl.shared.db.DatabaseDriverFactory
import de.tododl.shared.di.sharedModule
import de.tododl.shared.remote.HttpClientFactory
import org.koin.core.context.startKoin

fun main() {
    // WICHTIG: außerhalb von application{} aufrufen - application{} ist ein
    // Composable-Body und wird bei jeder Recomposition (z.B. beim Öffnen des
    // Einstellungen-Fensters) erneut ausgeführt. Stand startKoin hier drin,
    // crashte die App beim zweiten Durchlauf mit
    // "KoinApplicationAlreadyStartedException".
    startKoin {
        modules(sharedModule(DatabaseDriverFactory(), HttpClientFactory()), desktopConnectorsModule)
    }

    application {
        val windowState = rememberWindowState(
            position = WindowPosition(alignment = androidx.compose.ui.Alignment.Center),
            size = DpSize(1200.dp, 800.dp)
        )

        // Eigenständiges Fenster, jederzeit über Cmd+, (bzw. Menü "Tododl > Einstellungen…")
        // erreichbar - unabhängig davon, was gerade in der Hauptnavigation offen ist.
        // isDarkMode lebt hier (nicht mehr in TododlApp), damit Haupt- und
        // Einstellungen-Fenster denselben Zustand teilen.
        var showSettings by remember { mutableStateOf(false) }
        var isDarkMode by remember { mutableStateOf(true) }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Tododl",
            state = windowState
        ) {
            MenuBar {
                Menu("Tododl") {
                    Item(
                        "Einstellungen…",
                        shortcut = KeyShortcut(Key.Comma, meta = true),
                        onClick = { showSettings = true }
                    )
                }
            }
            TododlApp(
                isDarkMode = isDarkMode,
                onToggleDarkMode = { isDarkMode = !isDarkMode },
                onOpenSettings = { showSettings = true }
            )
        }

        if (showSettings) {
            DialogWindow(
                onCloseRequest = { showSettings = false },
                title = "Einstellungen",
                // Bewusst leicht versetzt zum Hauptfenster (nicht exakt zentriert
                // deckungsgleich) und etwas kleiner, damit das Hauptfenster
                // dahinter sichtbar bleibt statt komplett verdeckt zu wirken.
                state = rememberDialogState(
                    position = WindowPosition(x = 180.dp, y = 120.dp),
                    size = DpSize(880.dp, 620.dp)
                )
            ) {
                TododlTheme(darkTheme = isDarkMode) {
                    SettingsScreen(
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = { isDarkMode = !isDarkMode }
                    )
                }
            }
        }
    }
}
