package com.minima.os.ui.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.minima.os.data.entity.MemoryEntity
import com.minima.os.data.entity.PatternEntity
import com.minima.os.data.entity.PersonEntity
import com.minima.os.data.entity.PlaceEntity
import com.minima.os.data.memory.MemoryStats

@Composable
fun MemoryScreen(
    memories: List<MemoryEntity>,
    people: List<PersonEntity>,
    places: List<PlaceEntity>,
    patterns: List<PatternEntity>,
    stats: MemoryStats?,
    onDismiss: () -> Unit,
    onDeleteMemory: (String) -> Unit,
    onDeletePerson: (String) -> Unit,
    onDeletePlace: (String) -> Unit,
    onDeletePattern: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "People", "Places", "Patterns")

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        )

        // Content
        Column(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Handle + Header
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Memory",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (stats != null) {
                            Text(
                                "${stats.totalMemories} memories | ${stats.peopleCount} people | ${stats.placesCount} places",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Close", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tier badges
                if (stats != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TierBadge("STM", stats.stmCount, Color(0xFFF59E0B))
                        TierBadge("MTM", stats.mtmCount, Color(0xFF3B82F6))
                        TierBadge("LTM", stats.ltmCount, Color(0xFF10B981))
                    }
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF7C6FED),
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = @Composable {
                            Text(
                                title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Content
            when (selectedTab) {
                0 -> MemoryList(memories, onDeleteMemory)
                1 -> PeopleList(people, onDeletePerson)
                2 -> PlacesList(places, onDeletePlace)
                3 -> PatternsList(patterns, onDeletePattern)
            }
        }
    }
}

@Composable
private fun TierBadge(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text("$label: $count", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun MemoryList(memories: List<MemoryEntity>, onDelete: (String) -> Unit) {
    if (memories.isEmpty()) {
        EmptyState("No memories yet", "Use the app and I'll start learning")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(memories, key = { it.id }) { memory ->
            MemoryCard(
                icon = when (memory.category) {
                    "preference" -> Icons.Default.Favorite
                    "person" -> Icons.Default.Person
                    "context" -> Icons.Default.History
                    "fact" -> Icons.Default.Lightbulb
                    else -> Icons.Default.Memory
                },
                iconColor = when (memory.tier) {
                    "LTM" -> Color(0xFF10B981)
                    "MTM" -> Color(0xFF3B82F6)
                    else -> Color(0xFFF59E0B)
                },
                title = memory.key,
                subtitle = memory.value,
                badge = memory.tier,
                onDelete = { onDelete(memory.id) }
            )
        }
    }
}

@Composable
private fun PeopleList(people: List<PersonEntity>, onDelete: (String) -> Unit) {
    if (people.isEmpty()) {
        EmptyState("No people yet", "Message or mention someone and I'll remember them")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(people, key = { it.id }) { person ->
            MemoryCard(
                icon = Icons.Default.Person,
                iconColor = Color(0xFF8B5CF6),
                title = person.name,
                subtitle = listOfNotNull(
                    person.relationship?.let { "Relationship: $it" },
                    person.notes,
                    "Interactions: ${person.interactionCount}"
                ).joinToString(" | "),
                onDelete = { onDelete(person.id) }
            )
        }
    }
}

@Composable
private fun PlacesList(places: List<PlaceEntity>, onDelete: (String) -> Unit) {
    if (places.isEmpty()) {
        EmptyState("No places yet", "Mention locations and I'll remember them")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(places, key = { it.id }) { place ->
            MemoryCard(
                icon = Icons.Default.Place,
                iconColor = Color(0xFFEF4444),
                title = place.name,
                subtitle = listOfNotNull(
                    place.type?.let { "Type: $it" },
                    place.address,
                    "Visits: ${place.visitCount}"
                ).joinToString(" | "),
                onDelete = { onDelete(place.id) }
            )
        }
    }
}

@Composable
private fun PatternsList(patterns: List<PatternEntity>, onDelete: (String) -> Unit) {
    if (patterns.isEmpty()) {
        EmptyState("No patterns yet", "Use the app regularly and I'll detect patterns")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(patterns, key = { it.id }) { pattern ->
            MemoryCard(
                icon = Icons.Default.TrendingUp,
                iconColor = Color(0xFF06B6D4),
                title = pattern.description,
                subtitle = "Observed ${pattern.frequency}x | Confidence: ${(pattern.confidence * 100).toInt()}%",
                onDelete = { onDelete(pattern.id) }
            )
        }
    }
}

@Composable
private fun MemoryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    badge: String? = null,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = iconColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(iconColor.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Default.Delete,
                "Delete",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Memory,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(subtitle, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}
