package de.tododl.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import de.tododl.desktop.navigation.TododlApp
import de.tododl.shared.db.DatabaseDriverFactory
import de.tododl.shared.di.sharedModule
import de.tododl.shared.remote.HttpClientFactory
import org.koin.core.context.startKoin

fun main() = application {
    // Koin nur einmal beim App-Start initialisieren
    startKoin {
        modules(sharedModule(DatabaseDriverFactory(), HttpClientFactory()))
    }

    val windowState = rememberWindowState(
        position = WindowPosition(alignment = androidx.compose.ui.Alignment.Center),
        size = DpSize(1200.dp, 800.dp)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "Tododl",
        state = windowState
    ) {
        TododlApp()
    }
}
