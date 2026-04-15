package com.minima.os.ui.ooda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoAwesome
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
import com.minima.os.data.entity.TuningChangeEntity
import com.minima.os.data.ooda.OodaEngine
import com.minima.os.ui.theme.MinimaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OodaDashboard(
    summary: OodaEngine.Summary?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { onRefresh() }

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
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        Icons.Outlined.AutoAwesome, null,
                        tint = MinimaColors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Auto-tune",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MinimaColors.onSurface
                    )
                }
                IconButton(onClick = onDismiss) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Close, "Close",
                        tint = MinimaColors.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (summary == null) {
                    Text(
                        "Loading…",
                        fontSize = 13.sp,
                        color = MinimaColors.onSurfaceVariant
                    )
                } else {
                    SummaryHeader(summary)
                    Spacer(Modifier.height(4.dp))
                    DimensionSection("By intent", summary.stats.byIntent)
                    DimensionSection("By provider", summary.stats.byProvider)
                    DimensionSection("Voice vs text", summary.stats.byVoiceText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Proposed changes",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinimaColors.primary,
                        letterSpacing = 1.sp
                    )
                    if (summary.recentChanges.isEmpty()) {
                        Text(
                            "No proposals yet. The loop runs every ${OodaEngine.MIN_BATCH_SIZE} tasks.",
                            fontSize = 12.sp,
                            color = MinimaColors.onSurfaceVariant
                        )
                    } else {
                        summary.recentChanges.forEach { ChangeRow(it) }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(s: OodaEngine.Summary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MinimaColors.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            StatCell("${s.totalOutcomes}", "tasks")
            StatCell("${(s.stats.successRate * 100).toInt()}%", "success")
            StatCell("${s.stats.avgTotalMs}ms", "avg")
            StatCell("${s.outcomesUntilNextBatch}", "to batch")
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MinimaColors.onSurface)
        Text(label, fontSize = 10.sp, color = MinimaColors.onSurfaceVariant, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun DimensionSection(title: String, buckets: Map<String, OodaEngine.BucketStats>) {
    if (buckets.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MinimaColors.primary.copy(alpha = 0.7f),
            letterSpacing = 1.2.sp
        )
        buckets.entries.sortedByDescending { it.value.count }.forEach { (name, b) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MinimaColors.surfaceContainer.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    name,
                    fontSize = 12.sp,
                    color = MinimaColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "n=${b.count} · ${(b.successRate * 100).toInt()}% · ${b.avgMs}ms",
                    fontSize = 11.sp,
                    color = MinimaColors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChangeRow(c: TuningChangeEntity) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MinimaColors.surfaceContainerHigh.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                c.param,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MinimaColors.primary
            )
            Text(
                if (c.applied) "applied" else "proposal",
                fontSize = 10.sp,
                color = if (c.applied) Color(0xFF7FD48F) else MinimaColors.onSurfaceVariant
            )
        }
        Text(
            "${c.previousValue} → ${c.proposedValue}",
            fontSize = 11.sp,
            color = MinimaColors.onSurface
        )
        Text(
            c.reason,
            fontSize = 11.sp,
            color = MinimaColors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            fmt.format(Date(c.timestamp)) + "  ·  batch #${c.batchId}",
            fontSize = 10.sp,
            color = MinimaColors.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
