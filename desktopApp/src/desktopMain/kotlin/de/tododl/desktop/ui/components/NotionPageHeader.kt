package de.tododl.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tododl.desktop.ui.theme.LocalNotionColors

@Composable
fun NotionPageHeader(
    icon: ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    title: String,
    subtitle: String? = null,
    onAddAction: (() -> Unit)? = null,
    addActionLabel: String = "Neu",
    badgeLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val notionColors = LocalNotionColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Notion-Style Page Icon Container
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        badgeLabel?.let { badge ->
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = notionColors.hoverHighlight,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = notionColors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = notionColors.textSecondary
                        )
                    }
                }
            }

            if (onAddAction != null) {
                Button(
                    onClick = onAddAction,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(addActionLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
