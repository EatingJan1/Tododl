package de.tododl.desktop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.ui.theme.LocalNotionColors
import de.tododl.shared.model.Bereich

@Composable
fun NotionTopBar(
    bereiche: List<Bereich>,
    activeBereich: Bereich?,
    onSelectBereich: (Bereich) -> Unit,
    onAddBereich: () -> Unit,
    breadcrumbs: List<String>,
    onBreadcrumbClick: (index: Int) -> Unit,
    isSidebarVisible: Boolean,
    onToggleSidebar: () -> Unit,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onOpenServerLogin: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val notionColors = LocalNotionColors.current
    var bereichDropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sidebar Toggle
                IconButton(
                    onClick = onToggleSidebar,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isSidebarVisible) Icons.Default.MenuOpen else Icons.Default.Menu,
                        contentDescription = "Seitenleiste umschalten",
                        tint = notionColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück",
                            tint = notionColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Bereich Switcher Dropdown (Top left area switcher)
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { bereichDropdownExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeBereich?.title ?: "Bereich wählen",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = null,
                            tint = notionColors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = bereichDropdownExpanded,
                        onDismissRequest = { bereichDropdownExpanded = false }
                    ) {
                        Text(
                            text = "BEREICHE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = notionColors.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )

                        bereiche.forEach { b ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp).padding(end = 8.dp),
                                            tint = if (b.id == activeBereich?.id) MaterialTheme.colorScheme.primary else notionColors.textSecondary
                                        )
                                        Text(
                                            text = b.title,
                                            fontWeight = if (b.id == activeBereich?.id) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                },
                                onClick = {
                                    onSelectBereich(b)
                                    bereichDropdownExpanded = false
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = notionColors.border)

                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp).padding(end = 8.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text("Neuer Bereich...", color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                bereichDropdownExpanded = false
                                onAddBereich()
                            }
                        )
                    }
                }

                // Divider line between area switcher and breadcrumbs
                Text(
                    text = "/",
                    color = notionColors.border,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Breadcrumb Navigation Bar
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    breadcrumbs.forEachIndexed { index, title ->
                        if (index > 0) {
                            Text(
                                text = " / ",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = notionColors.textSecondary
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (index == breadcrumbs.lastIndex) FontWeight.Medium else FontWeight.Normal,
                                fontSize = 13.sp
                            ),
                            color = if (index == breadcrumbs.lastIndex) MaterialTheme.colorScheme.onSurface else notionColors.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onBreadcrumbClick(index) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Top Right Action Buttons
                IconButton(
                    onClick = onOpenServerLogin,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Projektserver",
                        tint = notionColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = onToggleDarkMode,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Design-Modus umschalten",
                        tint = notionColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = notionColors.border, thickness = 1.dp)
        }
    }
}
