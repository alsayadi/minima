package com.minima.os.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minima.os.core.bus.NotificationHub
import com.minima.os.core.model.NotificationCategory
import com.minima.os.core.model.NotificationInfo
import com.minima.os.ui.theme.MinimaColors

/**
 * Top-of-feed strip surfacing unread notifications.
 *
 * Each card shows the source app + title/text, tap opens the notification's
 * own contentIntent (so the user lands where the app intended), and the X
 * dismisses it both from the shade (via NotificationListenerService.cancelNotification)
 * and from the Minima view.
 *
 * We cap the list at a small number because the home screen is a *surface*,
 * not a replica of the shade — the full list lives in the system's shade if
 * the user wants to see everything.
 */
@Composable
fun NotificationStrip(
    modifier: Modifier = Modifier,
    maxItems: Int = 4,
) {
    val notifications by NotificationHub.notifications.collectAsState()

    // Filter: drop ongoing (music players, etc.) — they're noise in a task surface.
    // Drop anything with no title/text (often OS spam). Drop group-summary
    // synthetic notifications — our own grouping replaces them.
    val visible = notifications
        .asSequence()
        .filter { !it.isOngoing }
        .filter { !it.isGroupSummary }
        .filter { it.title.isNotBlank() || it.text.isNotBlank() }
        .toList()

    if (visible.isEmpty()) return

    // Collapse by groupKey so 5 messages from "Mom" become one "Mom (5)"
    // card. Order is preserved by latest member's timestamp. Items without
    // a groupKey are kept individual.
    val grouped: List<List<NotificationInfo>> = buildGroups(visible).take(maxItems)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        grouped.forEach { group ->
            if (group.size == 1) {
                val n = group[0]
                NotificationCard(
                    info = n,
                    onOpen = { NotificationHub.open(n.id) },
                    onDismiss = { NotificationHub.dismiss(n.id) },
                    onReply = { text -> NotificationHub.reply(n.id, text) }
                )
            } else {
                NotificationGroupCard(group = group)
            }
        }
    }
}

/**
 * Collapse the flat list into groups keyed by [NotificationInfo.groupKey].
 * Preserves the original order: a group sits where its newest member sits.
 * Items with a null groupKey are each their own group.
 */
private fun buildGroups(input: List<NotificationInfo>): List<List<NotificationInfo>> {
    val groups = LinkedHashMap<String, MutableList<NotificationInfo>>()
    val singletons = mutableListOf<MutableList<NotificationInfo>>()
    input.forEach { n ->
        val key = n.groupKey
        if (key == null) {
            singletons.add(mutableListOf(n))
        } else {
            groups.getOrPut(key) { mutableListOf() }.add(n)
        }
    }
    // Merge: keep input order by walking the original list and emitting each
    // group on its first appearance.
    val seen = mutableSetOf<String>()
    val out = mutableListOf<List<NotificationInfo>>()
    var singletonIdx = 0
    input.forEach { n ->
        val key = n.groupKey
        if (key == null) {
            out.add(singletons[singletonIdx]); singletonIdx++
        } else if (seen.add(key)) {
            out.add(groups.getValue(key))
        }
    }
    return out
}

@Composable
private fun NotificationCard(
    info: NotificationInfo,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onReply: (String) -> Unit,
) {
    var replyMode by remember(info.id) { mutableStateOf(false) }
    var replyText by remember(info.id) { mutableStateOf("") }
    val focusRequester = remember(info.id) { FocusRequester() }
    LaunchedEffect(replyMode) {
        if (replyMode) runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MinimaColors.glass)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !replyMode) { onOpen() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Category icon — a visual anchor that groups notifications by kind
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MinimaColors.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconFor(info.category),
                    contentDescription = null,
                    tint = MinimaColors.primary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = info.appName.ifBlank { info.packageName },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinimaColors.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = info.title.ifBlank { info.text },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MinimaColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (info.title.isNotBlank() && info.text.isNotBlank()) {
                    Text(
                        text = info.text,
                        fontSize = 12.sp,
                        color = MinimaColors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Reply — only on notifications that expose a RemoteInput action.
            // Lets the user respond without opening the source app.
            if (info.canReply) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MinimaColors.primary.copy(alpha = if (replyMode) 0.30f else 0.14f))
                        .clickable { replyMode = !replyMode },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Reply,
                        contentDescription = "Reply",
                        tint = MinimaColors.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Dismiss — small, subtle; not the primary affordance (tap-body-to-open is)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MinimaColors.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Inline reply field — appears when the user taps the Reply pill.
        if (replyMode) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (replyText.isEmpty()) {
                        Text(
                            text = "Reply…",
                            fontSize = 13.sp,
                            color = MinimaColors.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                    BasicTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        textStyle = TextStyle(
                            color = MinimaColors.onSurface,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = SolidColor(MinimaColors.primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (replyText.isNotBlank()) { onReply(replyText.trim()); replyMode = false }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                val canSend = replyText.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MinimaColors.primary.copy(alpha = if (canSend) 0.85f else 0.18f))
                        .clickable(enabled = canSend) {
                            onReply(replyText.trim()); replyMode = false
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Send,
                        contentDescription = "Send",
                        tint = if (canSend) Color.White else MinimaColors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun iconFor(category: NotificationCategory): ImageVector = when (category) {
    NotificationCategory.MESSAGE -> Icons.Outlined.ChatBubbleOutline
    NotificationCategory.EMAIL -> Icons.Outlined.MailOutline
    NotificationCategory.SOCIAL -> Icons.Outlined.PeopleAlt
    NotificationCategory.CALENDAR -> Icons.Outlined.CalendarMonth
    NotificationCategory.TRANSPORT -> Icons.Outlined.DirectionsCar
    NotificationCategory.PROMO -> Icons.Outlined.LocalOffer
    NotificationCategory.SYSTEM -> Icons.Outlined.Settings
    NotificationCategory.NEWS -> Icons.Outlined.Campaign
    else -> Icons.Outlined.Notifications
}

/**
 * Stacked card for a conversation: shows the latest message + a "+N" badge.
 * Tap header to expand inline; expanded state lists each message with its own
 * Reply/dismiss controls. Group dismiss cancels every member at once.
 */
@Composable
private fun NotificationGroupCard(group: List<NotificationInfo>) {
    val sorted = group.sortedByDescending { it.timestamp }
    val head = sorted.first()
    val rest = sorted.drop(1)
    var expanded by remember(head.groupKey) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MinimaColors.glass)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Header row: same shape as a single card, but with a "+N" badge and
        // a tap-to-expand affordance. Tapping the body of the head card when
        // collapsed opens the latest one (mirrors shade behavior).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (!expanded) expanded = true else NotificationHub.open(head.id) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Stacked-card glyph: category icon with a faint badge underneath
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MinimaColors.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconFor(head.category),
                    contentDescription = null,
                    tint = MinimaColors.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = head.appName.ifBlank { head.packageName },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinimaColors.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // "+N" badge — visually identifies this is a group
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MinimaColors.primary.copy(alpha = 0.20f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "${group.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MinimaColors.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = head.title.ifBlank { head.text },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MinimaColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (head.title.isNotBlank() && head.text.isNotBlank()) {
                    Text(
                        text = head.text,
                        fontSize = 12.sp,
                        color = MinimaColors.onSurfaceVariant,
                        maxLines = if (expanded) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Dismiss-all — drops every member of the group from both the
            // strip and the system shade.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable {
                        group.forEach { NotificationHub.dismiss(it.id) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Dismiss all",
                    tint = MinimaColors.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Expanded body — render each older sibling as a thin row with its
        // own open/dismiss/reply affordances. We don't render Reply pills
        // here for brevity; users can tap the row to open the source app.
        if (expanded && rest.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            rest.forEach { sibling ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { NotificationHub.open(sibling.id) }
                        .padding(start = 44.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sibling.text.ifBlank { sibling.title },
                            fontSize = 13.sp,
                            color = MinimaColors.onSurface.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { NotificationHub.dismiss(sibling.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = MinimaColors.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}
