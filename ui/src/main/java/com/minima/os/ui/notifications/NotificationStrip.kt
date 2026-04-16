package com.minima.os.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    // And drop anything with no title/text (often OS spam).
    val visible = notifications
        .asSequence()
        .filter { !it.isOngoing }
        .filter { it.title.isNotBlank() || it.text.isNotBlank() }
        .take(maxItems)
        .toList()

    if (visible.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visible.forEach { n ->
            NotificationCard(
                info = n,
                onOpen = { NotificationHub.open(n.id) },
                onDismiss = { NotificationHub.dismiss(n.id) }
            )
        }
    }
}

@Composable
private fun NotificationCard(
    info: NotificationInfo,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MinimaColors.glass)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
