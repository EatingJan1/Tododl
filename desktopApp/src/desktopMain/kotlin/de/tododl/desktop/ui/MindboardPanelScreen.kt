package de.tododl.desktop.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.tododl.desktop.state.koinGet
import de.tododl.shared.model.MindCard
import de.tododl.shared.remote.SyncManager
import de.tododl.shared.repository.MindCardRepository
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Einfaches freies Board: Karten lassen sich per Drag verschieben.
 * Bewusst simpel gehalten - reicht als Basis für spätere Mindmap-Verknüpfungen.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun MindboardPanelScreen(panelId: String) {
    val repo = remember { koinGet<MindCardRepository>() }
    val syncManager = remember { koinGet<SyncManager>() }
    val scope = rememberCoroutineScope()
    val cards by repo.observeCards(panelId).collectAsState(initial = emptyList())

    Box(Modifier.fillMaxSize()) {
        cards.forEach { card ->
            var offset by remember(card.id) { mutableStateOf(Offset(card.posX, card.posY)) }
            Card(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(offset.x.toInt(), offset.y.toInt()) }
                    .width(180.dp)
                    .pointerInput(card.id) {
                        detectDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    repo.updatePosition(card.id, offset.x, offset.y)
                                    runCatching {
                                        syncManager.pushMindCardIfNeeded(
                                            card.copy(posX = offset.x, posY = offset.y)
                                        )
                                    }
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        }
                    }
            ) {
                Text(card.text, modifier = Modifier.padding(12.dp))
            }
        }

        FloatingActionButton(
            onClick = {
                scope.launch {
                    val card = MindCard(
                        id = Uuid.random().toString(),
                        panelId = panelId,
                        text = "Neue Notiz",
                        posX = 40f,
                        posY = 40f
                    )
                    repo.upsert(card)
                    runCatching { syncManager.pushMindCardIfNeeded(card) }
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Notiz hinzufügen")
        }
    }
}
