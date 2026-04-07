package com.minima.os.ui.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minima.os.data.memory.ContextEngine
import com.minima.os.ui.theme.MinimaColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ContextSurface(
    contextData: ContextEngine.ContextData?,
    notificationCount: Int,
    onInsightTap: ((String) -> Unit)? = null,
    userName: String? = null,
    temperature: String? = null,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); tick = System.currentTimeMillis() }
    }

    val now = remember(tick) { Calendar.getInstance() }
    val hour = now.get(Calendar.HOUR_OF_DAY)

    val timeStr = remember(tick) { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()) }
    val dateStr = remember(tick) { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()) }

    val greeting = contextData?.greeting ?: run {
        val base = when {
            hour < 5 -> "Good night"; hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"; else -> "Good evening"
        }
        if (!userName.isNullOrBlank()) "$base, $userName" else base
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 60.dp)
    ) {
        // Greeting pill — sits above clock
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MinimaColors.primary.copy(alpha = 0.10f))
                .border(1.dp, MinimaColors.primary.copy(alpha = 0.10f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = greeting,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MinimaColors.primary,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Big clock — Thin weight, ethereal
        Text(
            text = timeStr,
            fontSize = 86.sp,
            fontWeight = FontWeight.Thin,
            color = MinimaColors.onSurface.copy(alpha = 0.90f),
            lineHeight = 86.sp,
            letterSpacing = (-3).sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Date row with dot separator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Text(
                text = dateStr,
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                color = MinimaColors.onSurfaceVariant
            )
            if (!temperature.isNullOrBlank()) {
                Text(
                    text = " · ",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light,
                    color = MinimaColors.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = temperature,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light,
                    color = MinimaColors.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Insight chips — horizontal scroll
        val cards = contextData?.insightCards ?: emptyList()
        AnimatedVisibility(
            visible = cards.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(cards) { card ->
                    InsightChip(
                        card = card,
                        onClick = { card.action?.let { onInsightTap?.invoke(it) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightChip(
    card: ContextEngine.InsightCard,
    onClick: () -> Unit
) {
    val icon = when (card.icon) {
        "morning" -> Icons.Outlined.WbSunny
        "evening" -> Icons.Outlined.WbTwilight
        "night" -> Icons.Outlined.NightsStay
        "focus" -> Icons.Outlined.CenterFocusStrong
        "food" -> Icons.Outlined.Restaurant
        "calendar" -> Icons.Outlined.CalendarToday
        "person" -> Icons.Outlined.Person
        "heart" -> Icons.Outlined.Favorite
        "pattern" -> Icons.Outlined.TrendingUp
        "brain" -> Icons.Outlined.Psychology
        "coffee" -> Icons.Outlined.Coffee
        else -> Icons.Outlined.AutoAwesome
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MinimaColors.surfaceContainerLow)
            .border(1.dp, MinimaColors.outlineVariant.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .then(if (card.action != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MinimaColors.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = card.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MinimaColors.onSurface.copy(alpha = 0.80f),
            letterSpacing = 0.3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
