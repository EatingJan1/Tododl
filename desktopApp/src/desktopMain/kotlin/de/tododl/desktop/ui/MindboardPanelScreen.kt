package de.tododl.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.state.koinGet
import de.tododl.desktop.ui.components.NotionPageHeader
import de.tododl.desktop.ui.theme.LocalNotionColors
import de.tododl.shared.model.MindCard
import de.tododl.shared.remote.SyncManager
import de.tododl.shared.repository.MindCardRepository
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun MindboardPanelScreen(panelId: String) {
    val repo = remember { koinGet<MindCardRepository>() }
    val syncManager = remember { koinGet<SyncManager>() }
    val scope = rememberCoroutineScope()
    val notionColors = LocalNotionColors.current
    val cards by repo.observeCards(panelId).collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        NotionPageHeader(
            icon = Icons.Default.Lightbulb,
            iconColor = notionColors.badgeMindboard,
            title = "Mindboard",
            subtitle = "Freie Notizkarten – frei verschiebbar",
            onAddAction = {
                scope.launch {
                    val card = MindCard(
                        id = Uuid.random().toString(),
                        panelId = panelId,
                        text = "Neue Notiz...",
                        posX = 40f + (cards.size * 20f),
                        posY = 40f + (cards.size * 20f)
                    )
                    repo.upsert(card)
                    runCatching { syncManager.pushMindCardIfNeeded(card) }
                }
            },
            addActionLabel = "Notizkarte anlegen",
            badgeLabel = "MINDBOARD"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            cards.forEach { card ->
                var offset by remember(card.id) { mutableStateOf(Offset(card.posX, card.posY)) }
                var cardText by remember(card.id) { mutableStateOf(card.text) }

                Surface(
                    modifier = Modifier
                        .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
                        .width(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, notionColors.border, RoundedCornerShape(8.dp))
                        .pointerInput(card.id) {
                            detectDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        repo.updatePosition(card.id, offset.x, offset.y)
                                        runCatching {
                                            syncManager.pushMindCardIfNeeded(
                                                card.copy(posX = offset.x, posY = offset.y, text = cardText)
                                            )
                                        }
                                    }
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                offset += dragAmount
                            }
                        },
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = notionColors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            IconButton(
                                onClick = { scope.launch { repo.delete(card.id) } },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Löschen",
                                    tint = notionColors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        TextField(
                            value = cardText,
                            onValueChange = { newText ->
                                cardText = newText
                                scope.launch {
                                    val updated = card.copy(text = newText, posX = offset.x, posY = offset.y)
                                    repo.upsert(updated)
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                        )
                    }
                }
            }
        }
    }
}
