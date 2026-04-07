package com.minima.os.ui.proactive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minima.os.data.memory.ProactiveEngine
import com.minima.os.ui.theme.MinimaColors

@Composable
fun ProactiveCardView(
    card: ProactiveEngine.ProactiveCard,
    onTap: ((String) -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MinimaColors.glass)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .then(if (card.action != null) Modifier.clickable { card.action?.let { onTap?.invoke(it) } } else Modifier)
    ) {
        // Ambient glow in top-right corner
        Box(
            modifier = Modifier
                .size(128.dp)
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-40).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MinimaColors.primary.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header: pulse dot + "Live Insight" + dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MinimaColors.primary)
                    )
                    Text(
                        text = "LIVE INSIGHT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MinimaColors.primary.copy(alpha = 0.80f),
                        letterSpacing = 1.2.sp
                    )
                }

                if (card.dismissable) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                        Icon(
                            Icons.Rounded.Close, "Dismiss",
                            tint = Color.White.copy(alpha = 0.30f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Text(
                text = card.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MinimaColors.onSurface,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = card.body,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = MinimaColors.onSurface.copy(alpha = 0.70f),
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (card.action != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "Tap to act",
                        fontSize = 11.sp,
                        color = MinimaColors.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        Icons.Outlined.ArrowForward,
                        null,
                        tint = MinimaColors.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProactiveCardList(
    cards: List<ProactiveEngine.ProactiveCard>,
    onCardTap: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(cards, key = { it.id }) { card ->
            ProactiveCardView(
                card = card,
                onTap = onCardTap,
                onDismiss = { onDismiss(card.id) }
            )
        }
    }
}
