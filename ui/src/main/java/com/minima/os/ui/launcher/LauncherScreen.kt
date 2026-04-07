package com.minima.os.ui.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minima.os.ui.approval.ApprovalSheet
import com.minima.os.ui.commandbar.CommandBar
import com.minima.os.ui.memory.MemoryScreen
import com.minima.os.ui.proactive.ProactiveCardList
import com.minima.os.ui.settings.SettingsSheet
import com.minima.os.ui.taskfeed.TaskCard
import com.minima.os.ui.theme.MinimaColors

@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAppDrawer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showMemory by remember { mutableStateOf(false) }

    // Memory data
    val memories by viewModel.memories.collectAsState(initial = emptyList())
    val people by viewModel.people.collectAsState(initial = emptyList())
    val places by viewModel.places.collectAsState(initial = emptyList())
    val patterns by viewModel.patterns.collectAsState(initial = emptyList())
    val memoryStats by viewModel.memoryStats.collectAsState()

    // Context surface v2
    val contextData by viewModel.contextData.collectAsState()
    val onboardingQuestions by viewModel.onboardingQuestions.collectAsState()
    val proactiveCards by viewModel.proactiveCards.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MinimaColors.surface)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        MinimaColors.surface
                    ),
                    radius = 1200f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -60 && scrollState.value == 0) showAppDrawer = true
                        }
                    }
            ) {
                // Context surface v2 — personalized, memory-driven
                // Long-press for settings, double-tap for memory
                @OptIn(ExperimentalFoundationApi::class)
                Box(modifier = Modifier.combinedClickable(
                    onClick = {},
                    onDoubleClick = { showMemory = true; viewModel.refreshMemoryStats() },
                    onLongClick = { showSettings = true }
                )) {
                    ContextSurface(
                        contextData = contextData,
                        notificationCount = 0,
                        userName = contextData?.userName,
                        temperature = contextData?.temperature,
                        onInsightTap = { command ->
                            viewModel.onCommandTextChanged(command)
                            viewModel.onSubmitCommand()
                        }
                    )
                }

                // Proactive cards
                if (proactiveCards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ProactiveCardList(
                        cards = proactiveCards,
                        onCardTap = { command ->
                            viewModel.onCommandTextChanged(command)
                            viewModel.onSubmitCommand()
                        },
                        onDismiss = { id -> viewModel.dismissProactiveCard(id) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Onboarding for new users, or smart suggestions
                if (onboardingQuestions.isNotEmpty() && uiState.taskHistory.isEmpty()) {
                    OnboardingFlow(
                        questions = onboardingQuestions,
                        onAnswer = { command -> viewModel.onOnboardingAnswer(command) },
                        onSkip = { viewModel.dismissOnboarding() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (uiState.taskHistory.isEmpty()) {
                    val suggestions = contextData?.suggestions ?: listOf(
                        "What's on my calendar?",
                        "Remind me to call Mom at 5pm",
                        "Check notifications",
                        "Tell me a joke"
                    )
                    QuickSuggestions(
                        suggestions = suggestions,
                        onSuggestionClick = { suggestion ->
                            viewModel.onCommandTextChanged(suggestion)
                            viewModel.onSubmitCommand()
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Task feed
                if (uiState.taskHistory.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.taskHistory.take(5).forEach { task ->
                            TaskCard(task = task)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Command bar (full width, pinned at bottom)
            CommandBar(
                text = uiState.commandText,
                onTextChange = viewModel::onCommandTextChanged,
                onSubmit = viewModel::onSubmitCommand,
                isProcessing = uiState.isProcessing,
                onAppsClick = { showAppDrawer = true },
                modifier = Modifier.fillMaxWidth()
            )

            // Swipe-up hint
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAppDrawer = true }
                    .padding(top = 8.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Swipe up for apps",
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // App drawer overlay
        AnimatedVisibility(
            visible = showAppDrawer,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            AppDrawer(
                apps = uiState.installedApps,
                onAppClick = { appLabel ->
                    showAppDrawer = false
                    viewModel.onCommandTextChanged("open $appLabel")
                    viewModel.onSubmitCommand()
                },
                onDismiss = { showAppDrawer = false }
            )
        }

        // Approval overlay
        AnimatedVisibility(
            visible = uiState.pendingApproval != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            uiState.pendingApproval?.let { request ->
                ApprovalSheet(
                    request = request,
                    onApprove = viewModel::onApprove,
                    onReject = viewModel::onReject
                )
            }
        }

        // Settings overlay
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            SettingsSheet(
                onDismiss = { showSettings = false },
                onApiKeySaved = { key -> viewModel.onApiKeySaved(key) },
                onSensitivityChanged = { viewModel.onSensitivityChanged(it) }
            )
        }

        // Memory overlay
        AnimatedVisibility(
            visible = showMemory,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            MemoryScreen(
                memories = memories,
                people = people,
                places = places,
                patterns = patterns,
                stats = memoryStats,
                onDismiss = { showMemory = false },
                onDeleteMemory = viewModel::deleteMemory,
                onDeletePerson = viewModel::deletePerson,
                onDeletePlace = viewModel::deletePlace,
                onDeletePattern = viewModel::deletePattern
            )
        }
    }
}

@Composable
private fun QuickSuggestions(
    suggestions: List<String> = emptyList(),
    onSuggestionClick: (String) -> Unit
) {
    val categories = listOf("AI SUGGESTION", "UTILITY", "KNOWLEDGE", "ROUTINE")
    val icons = listOf(
        Icons.Outlined.AutoAwesome,
        Icons.Outlined.DirectionsCar,
        Icons.Outlined.MusicNote,
        Icons.Outlined.SelfImprovement
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Your day is clear.",
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
            color = MinimaColors.onSurface.copy(alpha = 0.92f),
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = "What can I help you discover?",
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            color = MinimaColors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        suggestions.forEachIndexed { index, suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MinimaColors.surfaceContainerHigh.copy(alpha = 0.40f))
                    .clickable { onSuggestionClick(suggestion) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = categories.getOrElse(index) { "SUGGESTION" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MinimaColors.primary.copy(alpha = 0.60f),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"$suggestion\"",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Light,
                        fontStyle = FontStyle.Italic,
                        color = MinimaColors.onSurface
                    )
                }
                Icon(
                    icons.getOrElse(index) { Icons.Outlined.AutoAwesome },
                    contentDescription = null,
                    tint = MinimaColors.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
