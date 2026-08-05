package de.tododl.desktop.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import de.tododl.desktop.ui.BereichListeScreen
import de.tododl.desktop.ui.GruppenScreen
import de.tododl.desktop.ui.MindboardPanelScreen
import de.tododl.desktop.ui.NodeBaumScreen
import de.tododl.desktop.ui.ProjektListeScreen
import de.tododl.desktop.ui.ServerListScreen
import de.tododl.desktop.ui.TodoListPanelScreen
import de.tododl.desktop.ui.theme.TododlTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TododlApp() {
    TododlTheme {
        // Einfacher Backstack als Liste; letztes Element = aktueller Screen
        var backstack by remember { mutableStateOf(listOf<Screen>(Screen.BereichListe)) }
        val current = backstack.last()

        fun push(screen: Screen) {
            backstack = backstack + screen
        }

        fun pop() {
            if (backstack.size > 1) backstack = backstack.dropLast(1)
        }

        val title = when (current) {
            is Screen.BereichListe -> "Tododl"
            is Screen.ServerLogin -> "Meine Server"
            is Screen.Gruppen -> "Gruppen – ${current.connectionName}"
            is Screen.ProjektListe -> current.bereichTitel
            is Screen.NodeBaum -> current.ordnerTitel ?: current.projektTitel
            is Screen.TodoListPanel -> current.panelTitel
            is Screen.MindboardPanel -> current.panelTitel
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (backstack.size > 1) {
                            IconButton(onClick = { pop() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                            }
                        }
                    },
                    actions = {
                        if (current is Screen.BereichListe) {
                            IconButton(onClick = { push(Screen.ServerLogin) }) {
                                Icon(Icons.Default.Cloud, contentDescription = "Projektserver")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Surface(modifier = Modifier.padding(padding)) {
                when (val screen = current) {
                    is Screen.BereichListe -> BereichListeScreen(
                        onBereichClick = { id, titel -> push(Screen.ProjektListe(id, titel)) }
                    )

                    is Screen.ServerLogin -> ServerListScreen(
                        onOpenGruppen = { id, name -> push(Screen.Gruppen(id, name)) }
                    )

                    is Screen.Gruppen -> GruppenScreen(connectionId = screen.connectionId)

                    is Screen.ProjektListe -> ProjektListeScreen(
                        bereichId = screen.bereichId,
                        onProjektClick = { id, titel -> push(Screen.NodeBaum(projectId = id, projektTitel = titel)) }
                    )

                    is Screen.NodeBaum -> NodeBaumScreen(
                        projectId = screen.projectId,
                        ordnerId = screen.ordnerId,
                        onOrdnerClick = { id, titel ->
                            push(screen.copy(ordnerId = id, ordnerTitel = titel))
                        },
                        onTodoListPanelClick = { id, titel -> push(Screen.TodoListPanel(id, titel)) },
                        onMindboardPanelClick = { id, titel -> push(Screen.MindboardPanel(id, titel)) }
                    )

                    is Screen.TodoListPanel -> TodoListPanelScreen(panelId = screen.panelId)

                    is Screen.MindboardPanel -> MindboardPanelScreen(panelId = screen.panelId)
                }
            }
        }
    }
}
