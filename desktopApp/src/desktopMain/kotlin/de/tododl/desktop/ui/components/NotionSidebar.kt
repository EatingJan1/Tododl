package de.tododl.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.state.koinGet
import de.tododl.desktop.ui.theme.LocalNotionColors
import de.tododl.shared.model.Node
import de.tododl.shared.model.NodeType
import de.tododl.shared.model.ProjectSource
import de.tododl.shared.model.Projekt
import de.tododl.shared.repository.NodeRepository
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun NotionSidebar(
    projekte: List<Projekt>,
    archivedProjekte: List<Projekt>,
    activeProjektId: String?,
    onSelectProjekt: (Projekt) -> Unit,
    onAddProjekt: () -> Unit,
    onArchiveProjekt: (Projekt) -> Unit,
    onUnarchiveProjekt: (Projekt) -> Unit,
    onDeleteProjekt: (Projekt) -> Unit,
    onSelectNode: (projectId: String, node: Node) -> Unit,
    modifier: Modifier = Modifier
) {
    val notionColors = LocalNotionColors.current
    var showArchivedSection by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(260.dp),
        color = notionColors.sidebarBackground
    ) {
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                // Section Title & Add Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PROJEKTE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.8.sp
                        ),
                        color = notionColors.textSecondary
                    )

                    IconButton(
                        onClick = onAddProjekt,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Neues Projekt",
                            tint = notionColors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Projects List with Nested Folder Accordions
                if (projekte.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Keine aktiven Projekte",
                            style = MaterialTheme.typography.bodySmall,
                            color = notionColors.textSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(projekte, key = { it.id }) { projekt ->
                            NotionProjectItem(
                                projekt = projekt,
                                isSelected = projekt.id == activeProjektId,
                                onSelect = { onSelectProjekt(projekt) },
                                onArchive = { onArchiveProjekt(projekt) },
                                onDelete = { onDeleteProjekt(projekt) },
                                onSelectNode = { node -> onSelectNode(projekt.id, node) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = notionColors.border, thickness = 1.dp)
                Spacer(Modifier.height(8.dp))

                // Archived Projects Collapsible Section
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showArchivedSection = !showArchivedSection }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (showArchivedSection) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = notionColors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = notionColors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Archiviert (${archivedProjekte.size})",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = notionColors.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AnimatedVisibility(
                        visible = showArchivedSection,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(start = 12.dp, top = 4.dp)) {
                            if (archivedProjekte.isEmpty()) {
                                Text(
                                    text = "Keine archivierten Projekte",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = notionColors.textSecondary,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                                )
                            } else {
                                archivedProjekte.forEach { proj ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = proj.title,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = notionColors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { onUnarchiveProjekt(proj) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Unarchive,
                                                contentDescription = "Wiederherstellen",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right boundary border of sidebar
            VerticalDivider(color = notionColors.border, thickness = 1.dp)
        }
    }
}

@Composable
private fun NotionProjectItem(
    projekt: Projekt,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onSelectNode: (Node) -> Unit
) {
    val notionColors = LocalNotionColors.current
    var isExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val nodeRepo = remember { koinGet<NodeRepository>() }

    val rootNodes by remember(projekt.id) {
        nodeRepo.observeRootNodes(projekt.id)
    }.collectAsState(initial = emptyList())

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor = when {
        isSelected -> notionColors.hoverHighlight
        isHovered -> notionColors.hoverHighlight
        else -> Color.Transparent
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .hoverable(interactionSource)
                .clickable { onSelect() }
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/Collapse Chevron (if project has root nodes or can expand)
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = notionColors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Project Icon
            Icon(
                imageVector = if (projekt.source == ProjectSource.SERVER) Icons.Default.Cloud else Icons.Default.Folder,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else notionColors.textSecondary,
                modifier = Modifier.size(16.dp)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = projekt.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Context Menu Button
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Optionen",
                        tint = notionColors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 8.dp))
                                Text("Archivieren", fontSize = 13.sp)
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onArchive()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 8.dp), tint = MaterialTheme.colorScheme.error)
                                Text("Löschen", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }

        // Nested Tree Accordion (Nodes directly under project)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(start = 22.dp)) {
                if (rootNodes.isEmpty()) {
                    Text(
                        text = "Keine Elemente",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = notionColors.textSecondary,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                } else {
                    rootNodes.forEach { node ->
                        NotionSidebarNodeItem(
                            node = node,
                            onSelectNode = onSelectNode
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotionSidebarNodeItem(
    node: Node,
    onSelectNode: (Node) -> Unit
) {
    val notionColors = LocalNotionColors.current
    var isExpanded by remember { mutableStateOf(false) }
    val nodeRepo = remember { koinGet<NodeRepository>() }

    val childNodes by remember(node.id) {
        if (node.isOrdner) nodeRepo.observeChildNodes(node.id) else emptyFlow()
    }.collectAsState(initial = emptyList())

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val icon = when (node.type) {
        NodeType.ORDNER -> Icons.Default.FolderOpen
        NodeType.PANEL_TODOLIST -> Icons.Default.Checklist
        NodeType.PANEL_MINDBOARD -> Icons.Default.Lightbulb
    }

    val iconColor = when (node.type) {
        NodeType.ORDNER -> notionColors.textSecondary
        NodeType.PANEL_TODOLIST -> MaterialTheme.colorScheme.primary
        NodeType.PANEL_MINDBOARD -> notionColors.badgeMindboard
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(if (isHovered) notionColors.hoverHighlight else Color.Transparent)
                .hoverable(interactionSource)
                .clickable { onSelectNode(node) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.isOrdner) {
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = notionColors.textSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(Modifier.width(2.dp))
            } else {
                Spacer(Modifier.width(18.dp))
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(14.dp)
            )

            Spacer(Modifier.width(6.dp))

            Text(
                text = node.title,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (node.isOrdner) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    childNodes.forEach { child ->
                        NotionSidebarNodeItem(
                            node = child,
                            onSelectNode = onSelectNode
                        )
                    }
                }
            }
        }
    }
}
