package com.minima.os.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minima.os.data.entity.CommandHistoryEntity
import com.minima.os.ui.theme.MinimaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommandHistorySheet(
    items: List<CommandHistoryEntity>,
    onRerun: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinimaColors.surface)
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp).height(4.dp)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MinimaColors.outline.copy(alpha = 0.4f))
                    .align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.History, null,
                        tint = MinimaColors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "History",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MinimaColors.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${items.size}",
                        fontSize = 12.sp,
                        color = MinimaColors.onSurfaceVariant
                    )
                }
                Row {
                    if (items.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onClear() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Clear",
                                fontSize = 12.sp,
                                color = MinimaColors.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close, "Close",
                            tint = MinimaColors.onSurfaceVariant
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No commands yet. Ask Minima something.",
                        fontSize = 13.sp,
                        color = MinimaColors.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items, key = { it.text }) { row -> HistoryRow(row, onRerun, onEdit, onDelete) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: CommandHistoryEntity,
    onRerun: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinimaColors.surfaceContainerLow)
            .clickable { onEdit(item.text) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = MinimaColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item.intent?.let { Text(it, fontSize = 9.sp, color = MinimaColors.primary, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp) }
                if (item.useCount > 1) {
                    Text("×${item.useCount}", fontSize = 9.sp, color = MinimaColors.onSurfaceVariant)
                }
                Text("·", fontSize = 9.sp, color = MinimaColors.onSurfaceVariant)
                Text(fmt.format(Date(item.lastUsedAt)), fontSize = 9.sp, color = MinimaColors.onSurfaceVariant)
                if (!item.success) {
                    Text("failed", fontSize = 9.sp, color = Color(0xFFE08A8A))
                }
            }
        }
        IconButton(onClick = { onRerun(item.text) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Replay, "Run again", tint = MinimaColors.primary, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = { onDelete(item.text) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Delete, "Delete", tint = MinimaColors.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}
