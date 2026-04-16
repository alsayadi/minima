package com.minima.os.ui.taskfeed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minima.os.core.model.Task
import com.minima.os.core.model.TaskState
import com.minima.os.core.model.StepStatus
import com.minima.os.ui.theme.MinimaColors

@Composable
fun TaskCard(
    task: Task,
    modifier: Modifier = Modifier
) {
    val isActive = task.state in listOf(
        TaskState.EXECUTING, TaskState.CLASSIFYING, TaskState.PLANNING
    )
    val isSuccess = task.state == TaskState.COMPLETED
    val isFailed = task.state in listOf(TaskState.FAILED, TaskState.CANCELLED)
    val isAnswer = task.intent?.type?.name == "ANSWER" || task.intent?.type?.name == "RECALL"

    val statusColor = when {
        isSuccess -> MinimaColors.success
        isFailed -> MinimaColors.error
        isActive -> MinimaColors.primary
        task.state == TaskState.AWAITING_APPROVAL -> MinimaColors.warning
        else -> MinimaColors.outline
    }

    val statusIcon = when {
        isSuccess -> Icons.Rounded.Check
        isFailed -> Icons.Rounded.Close
        else -> Icons.Rounded.AutoAwesome
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MinimaColors.glass)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        // Header: user input + status icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "\"${task.input}\"",
                color = MinimaColors.onSurfaceVariant,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))

            if (isActive) {
                // Bouncing dots for processing
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MinimaColors.primary)
                        )
                    }
                }
            } else {
                Icon(
                    statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Result content
        val resultText = when {
            isSuccess -> {
                val answerText = task.steps
                    .filter { it.status == StepStatus.COMPLETED }
                    .mapNotNull { it.result?.data?.get("answer") }
                    .firstOrNull()

                if (answerText != null) {
                    answerText.take(400)
                } else {
                    val results = task.steps
                        .filter { it.status == StepStatus.COMPLETED }
                        .mapNotNull { it.result?.data?.entries?.firstOrNull() }
                    if (results.isNotEmpty()) {
                        results.joinToString(" ") { it.value }.take(100)
                    } else "Done"
                }
            }
            isFailed -> task.error?.take(80) ?: "Something went wrong"
            isActive -> when (task.state) {
                TaskState.CLASSIFYING -> "Understanding..."
                TaskState.PLANNING -> "Planning..."
                TaskState.EXECUTING -> "Running..."
                else -> "Working..."
            }
            task.state == TaskState.AWAITING_APPROVAL -> "Needs your approval"
            else -> "Queued"
        }

        if (isAnswer && isSuccess) {
            // Styled answer with accent left border
            Row {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .heightIn(min = 16.dp)
                        .background(MinimaColors.primary.copy(alpha = 0.20f))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = resultText,
                        color = MinimaColors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 20.sp,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Copy / Share actions for answers — the 'Summarize then paste in Slack'
            // flow is the primary use case, so make that one tap.
            if (resultText.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                TaskResultActions(resultText = resultText)
            }
        } else {
            // Status row with dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = resultText,
                    color = if (isFailed) MinimaColors.error.copy(alpha = 0.80f)
                           else MinimaColors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * Tiny action row under a completed answer. Keeps the visual weight low so
 * it doesn't compete with the answer text, but puts Copy and Share one tap
 * away for the common "summarize → paste elsewhere" flow.
 */
@Composable
private fun TaskResultActions(resultText: String) {
    val ctx = LocalContext.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 14.dp)
    ) {
        ResultActionChip(
            icon = Icons.Outlined.ContentCopy,
            label = "Copy",
        ) {
            copyToClipboard(ctx, resultText)
            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
        }
        ResultActionChip(
            icon = Icons.Outlined.IosShare,
            label = "Share",
        ) {
            shareText(ctx, resultText)
        }
    }
}

@Composable
private fun ResultActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MinimaColors.onSurfaceVariant,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            color = MinimaColors.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("Minima", text))
}

private fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(chooser) }
}
