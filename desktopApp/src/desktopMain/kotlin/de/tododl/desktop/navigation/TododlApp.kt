package de.tododl.desktop.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tododl.desktop.state.koinGet
import de.tododl.desktop.ui.*
import de.tododl.desktop.ui.components.NotionSidebar
import de.tododl.desktop.ui.components.NotionTopBar
import de.tododl.desktop.ui.theme.TododlTheme
import de.tododl.shared.model.Bereich
import de.tododl.shared.model.NodeType
import de.tododl.shared.model.ProjectSource
import de.tododl.shared.model.Projekt
import de.tododl.shared.repository.BereichRepository
import de.tododl.shared.repository.ProjektRepository
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun TododlApp() {
    val bereichRepo = remember { koinGet<BereichRepository>() }
    val projektRepo = remember { koinGet<ProjektRepository>() }
    val scope = rememberCoroutineScope()

    val bereiche by bereichRepo.observeBereiche().collectAsState(initial = emptyList())

    var isDarkMode by remember { mutableStateOf(true) }
    var isSidebarVisible by remember { mutableStateOf(true) }
    var selectedBereichId by remember { mutableStateOf<String?>(null) }
    var showNewBereichDialog by remember { mutableStateOf(false) }
    var showNewProjektDialog by remember { mutableStateOf(false) }

    // Auto-select first area if none selected
    val activeBereich = bereiche.firstOrNull { it.id == selectedBereichId } ?: bereiche.firstOrNull()
    LaunchedEffect(activeBereich?.id) {
        if (selectedBereichId == null && activeBereich != null) {
            selectedBereichId = activeBereich.id
        }
    }

    val projekte by remember(activeBereich?.id) {
        activeBereich?.let { projektRepo.observeProjekte(it.id) } ?: emptyFlow()
    }.collectAsState(initial = emptyList())

    val archivedProjekte by remember(activeBereich?.id) {
        activeBereich?.let { projektRepo.observeArchivedProjekte(it.id) } ?: emptyFlow()
    }.collectAsState(initial = emptyList())

    // State-basierter Navigation-Backstack
    var backstack by remember { mutableStateOf(listOf<Screen>(Screen.BereichListe)) }
    val current = backstack.last()

    // Sync backstack with active Bereich when area changes
    LaunchedEffect(activeBereich?.id) {
        if (activeBereich != null && (current is Screen.BereichListe || current is Screen.ProjektListe)) {
            backstack = listOf(Screen.ProjektListe(activeBereich.id, activeBereich.title))
        }
    }

    fun push(screen: Screen) {
        backstack = backstack + screen
    }

    fun pop() {
        if (backstack.size > 1) backstack = backstack.dropLast(1)
    }

    val activeProjektId = when (current) {
        is Screen.NodeBaum -> current.projectId
        else -> null
    }

    val breadcrumbs = when (current) {
        is Screen.BereichListe -> listOf("Bereiche")
        is Screen.ServerLogin -> listOf("Meine Server")
        is Screen.Gruppen -> listOf("Meine Server", current.connectionName)
        is Screen.ProjektListe -> listOf(current.bereichTitel, "Projekte")
        is Screen.NodeBaum -> listOfNotNull(
            activeBereich?.title,
            current.projektTitel,
            current.ordnerTitel
        )
        is Screen.TodoListPanel -> listOfNotNull(
            activeBereich?.title,
            current.panelTitel
        )
        is Screen.MindboardPanel -> listOfNotNull(
            activeBereich?.title,
            current.panelTitel
        )
    }

    TododlTheme(darkTheme = isDarkMode) {
        Scaffold(
            topBar = {
                NotionTopBar(
                    bereiche = bereiche,
                    activeBereich = activeBereich,
                    onSelectBereich = { b ->
                        selectedBereichId = b.id
                        backstack = listOf(Screen.ProjektListe(b.id, b.title))
                    },
                    onAddBereich = { showNewBereichDialog = true },
                    breadcrumbs = breadcrumbs,
                    onBreadcrumbClick = { index ->
                        if (index == 0 && activeBereich != null) {
                            backstack = listOf(Screen.ProjektListe(activeBereich.id, activeBereich.title))
                        }
                    },
                    isSidebarVisible = isSidebarVisible,
                    onToggleSidebar = { isSidebarVisible = !isSidebarVisible },
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = { isDarkMode = !isDarkMode },
                    onOpenServerLogin = { push(Screen.ServerLogin) },
                    onBack = if (backstack.size > 1) { { pop() } } else null
                )
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Collapsible Notion Sidebar
                AnimatedVisibility(
                    visible = isSidebarVisible,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    NotionSidebar(
                        projekte = projekte,
                        archivedProjekte = archivedProjekte,
                        activeProjektId = activeProjektId,
                        onSelectProjekt = { p ->
                            backstack = listOf(
                                Screen.ProjektListe(p.bereichId, activeBereich?.title ?: "Bereich"),
                                Screen.NodeBaum(projectId = p.id, projektTitel = p.title)
                            )
                        },
                        onAddProjekt = { showNewProjektDialog = true },
                        onArchiveProjekt = { p ->
                            scope.launch { projektRepo.archive(p.id) }
                        },
                        onUnarchiveProjekt = { p ->
                            scope.launch { projektRepo.unarchive(p.id) }
                        },
                        onDeleteProjekt = { p ->
                            scope.launch { projektRepo.delete(p.id) }
                        },
                        onSelectNode = { projectId, node ->
                            val proj = projekte.firstOrNull { it.id == projectId }
                            val projTitel = proj?.title ?: "Projekt"
                            when (node.type) {
                                NodeType.ORDNER -> push(
                                    Screen.NodeBaum(
                                        projectId = projectId,
                                        projektTitel = projTitel,
                                        ordnerId = node.id,
                                        ordnerTitel = node.title
                                    )
                                )
                                NodeType.PANEL_TODOLIST -> push(Screen.TodoListPanel(node.id, node.title))
                                NodeType.PANEL_MINDBOARD -> push(Screen.MindboardPanel(node.id, node.title))
                            }
                        }
                    )
                }

                // Main Content Display Area
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val screen = current) {
                        is Screen.BereichListe -> BereichListeScreen(
                            onBereichClick = { id, titel ->
                                selectedBereichId = id
                                push(Screen.ProjektListe(id, titel))
                            }
                        )

                        is Screen.ServerLogin -> ServerListScreen(
                            onOpenGruppen = { id, name -> push(Screen.Gruppen(id, name)) }
                        )

                        is Screen.Gruppen -> GruppenScreen(connectionId = screen.connectionId)

                        is Screen.ProjektListe -> ProjektListeScreen(
                            bereichId = screen.bereichId,
                            bereichTitel = screen.bereichTitel,
                            onProjektClick = { id, titel ->
                                push(Screen.NodeBaum(projectId = id, projektTitel = titel))
                            }
                        )

                        is Screen.NodeBaum -> NodeBaumScreen(
                            projectId = screen.projectId,
                            ordnerId = screen.ordnerId,
                            ordnerTitel = screen.ordnerTitel,
                            projektTitel = screen.projektTitel,
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

        // Dialog for creating a new Bereich from TopBar or Dialog
        if (showNewBereichDialog) {
            NeuerBereichDialog(
                onDismiss = { showNewBereichDialog = false },
                onConfirm = { titel ->
                    scope.launch {
                        val newB = Bereich(
                            id = Uuid.random().toString(),
                            title = titel,
                            position = bereiche.size
                        )
                        bereichRepo.upsert(newB)
                        selectedBereichId = newB.id
                    }
                    showNewBereichDialog = false
                }
            )
        }

        // Dialog for creating a new Projekt from Sidebar
        if (showNewProjektDialog && activeBereich != null) {
            NeuesProjektDialog(
                onDismiss = { showNewProjektDialog = false },
                onConfirm = { titel ->
                    scope.launch {
                        projektRepo.upsert(
                            Projekt(
                                id = Uuid.random().toString(),
                                bereichId = activeBereich.id,
                                title = titel,
                                source = ProjectSource.LOCAL,
                                position = projekte.size
                            )
                        )
                    }
                    showNewProjektDialog = false
                }
            )
        }
    }
}

@Composable
private fun NeuerBereichDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuen Bereich anlegen") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name (z. B. Verein, Privat, Arbeit)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Anlegen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun NeuesProjektDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Projekt anlegen") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Projektname (z. B. Maibaum 2026)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Anlegen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
