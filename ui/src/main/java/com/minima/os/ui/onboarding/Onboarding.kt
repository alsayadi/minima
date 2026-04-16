package com.minima.os.ui.onboarding

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minima.os.model.provider.Provider
import com.minima.os.ui.theme.MinimaColors

private const val PREFS = "minima_prefs"
private const val KEY_DONE = "onboarding_completed"

/** Returns true if the user has already finished onboarding OR has a key for the selected provider. */
fun hasCompletedOnboarding(context: Context): Boolean {
    val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (p.getBoolean(KEY_DONE, false)) return true
    val provider = p.getString("llm_provider", Provider.OPENAI.name) ?: Provider.OPENAI.name
    val secure = com.minima.os.data.security.SecurePrefs.get(context)
    return !secure.getString("api_key_$provider").isNullOrBlank()
        || !secure.getString("openai_api_key").isNullOrBlank()
        // Legacy fallback — keys may still be mid-migration
        || !p.getString("api_key_$provider", null).isNullOrBlank()
        || !p.getString("openai_api_key", null).isNullOrBlank()
}

private fun markCompleted(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_DONE, true).apply()
}

@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var provider by remember { mutableStateOf(Provider.OPENAI) }
    var apiKey by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MinimaColors.surface)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1A2E), MinimaColors.surface),
                    radius = 1500f
                )
            )
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally(tween(400)) { it / 2 } + fadeIn(tween(300)))
                    .togetherWith(slideOutHorizontally(tween(400)) { -it / 2 } + fadeOut(tween(200)))
            },
            label = "onboarding-step"
        ) { s ->
            when (s) {
                0 -> Welcome(onNext = { step = 1 })
                1 -> PickProvider(
                    selected = provider,
                    onSelect = { provider = it },
                    onNext = { step = 2 },
                    onBack = { step = 0 }
                )
                2 -> ApiKeyStep(
                    provider = provider,
                    apiKey = apiKey,
                    onKeyChange = { apiKey = it },
                    onFinish = { save ->
                        if (save && apiKey.isNotBlank()) {
                            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("llm_provider", provider.name)
                                .apply()
                            // API key → encrypted store
                            val secure = com.minima.os.data.security.SecurePrefs.get(ctx)
                            secure.putString("api_key_${provider.name}", apiKey.trim())
                            if (provider == Provider.OPENAI) {
                                secure.putString("openai_api_key", apiKey.trim())
                            }
                        }
                        markCompleted(ctx)
                        onDone()
                    },
                    onBack = { step = 1 }
                )
            }
        }

        // Progress dots pinned near bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == step) 24.dp else 6.dp, 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (i == step) MinimaColors.primary
                            else MinimaColors.primary.copy(alpha = 0.25f)
                        )
                        .animateContentSize()
                )
            }
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MinimaColors.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.AutoAwesome, null,
                tint = MinimaColors.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Minima",
            fontSize = 48.sp,
            fontWeight = FontWeight.Thin,
            color = MinimaColors.onSurface,
            letterSpacing = (-1).sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "An AI-first launcher.",
            fontSize = 16.sp,
            fontWeight = FontWeight.Light,
            color = MinimaColors.onSurfaceVariant
        )
        Spacer(Modifier.height(40.dp))

        FeatureRow(Icons.Outlined.Mic, "Speak or type anything",
            "Weather, calendar, reminders, flashlight — 21 built-in actions.")
        Spacer(Modifier.height(20.dp))
        FeatureRow(Icons.Outlined.AutoAwesome, "Minima remembers",
            "Names, places, preferences, patterns — all stored on device.")
        Spacer(Modifier.height(20.dp))
        FeatureRow(Icons.Outlined.Lock, "Your keys, your data",
            "Bring your own LLM key. Nothing goes to Minima servers.")

        Spacer(Modifier.weight(1f))
        PrimaryButton("Get started", onClick = onNext)
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MinimaColors.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MinimaColors.primary, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinimaColors.onSurface)
            Text(body, fontSize = 12.sp, fontWeight = FontWeight.Normal,
                color = MinimaColors.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun PickProvider(
    selected: Provider,
    onSelect: (Provider) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Pick your AI",
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
            color = MinimaColors.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose any provider — you'll bring your own key. Groq is free if you're new.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = MinimaColors.onSurfaceVariant,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(28.dp))
        Provider.values().forEach { p ->
            val isSel = p == selected
            val tag = when (p) {
                Provider.GROQ -> "Free tier"
                Provider.OPENAI -> "Most popular"
                Provider.ANTHROPIC -> "Best reasoning"
                Provider.GEMINI -> "Fast, multimodal"
                Provider.DEEPSEEK -> "Cheap"
                Provider.OPENROUTER -> "Many models"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSel) MinimaColors.primary.copy(alpha = 0.16f)
                        else MinimaColors.surfaceContainerLow
                    )
                    .border(
                        1.dp,
                        if (isSel) MinimaColors.primary.copy(alpha = 0.5f)
                        else Color.Transparent,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(p) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(p.displayName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinimaColors.onSurface)
                    Text(p.defaultModel, fontSize = 11.sp, color = MinimaColors.onSurfaceVariant)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MinimaColors.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(tag, fontSize = 10.sp, color = MinimaColors.primary, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Back", modifier = Modifier.weight(1f), onClick = onBack)
            PrimaryButton("Continue", modifier = Modifier.weight(2f), onClick = onNext)
        }
    }
}

@Composable
private fun ApiKeyStep(
    provider: Provider,
    apiKey: String,
    onKeyChange: (String) -> Unit,
    onFinish: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val hint = when (provider) {
        Provider.OPENAI -> "Visit platform.openai.com/api-keys"
        Provider.GROQ -> "Visit console.groq.com/keys — free tier, no card required"
        Provider.ANTHROPIC -> "Visit console.anthropic.com/settings/keys"
        Provider.GEMINI -> "Visit aistudio.google.com/app/apikey — free tier"
        Provider.DEEPSEEK -> "Visit platform.deepseek.com/api_keys"
        Provider.OPENROUTER -> "Visit openrouter.ai/keys"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Paste your key",
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
            color = MinimaColors.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            fontSize = 14.sp,
            color = MinimaColors.onSurfaceVariant,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MinimaColors.surfaceContainerLow)
                .border(1.dp, MinimaColors.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (apiKey.isEmpty()) {
                Text(
                    "sk-...",
                    fontSize = 14.sp,
                    color = MinimaColors.onSurface.copy(alpha = 0.3f)
                )
            }
            BasicTextField(
                value = apiKey,
                onValueChange = onKeyChange,
                textStyle = TextStyle(
                    color = MinimaColors.onSurface,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(MinimaColors.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onFinish(true) }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Stored only on this device. Minima sends nothing to its own servers — just your command + memory context to the provider you picked.",
            fontSize = 11.sp,
            color = MinimaColors.onSurfaceVariant.copy(alpha = 0.7f),
            lineHeight = 16.sp
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Skip", modifier = Modifier.weight(1f), onClick = { onFinish(false) })
            PrimaryButton(
                if (apiKey.isNotBlank()) "Finish" else "Skip for now",
                modifier = Modifier.weight(2f),
                onClick = { onFinish(apiKey.isNotBlank()) }
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MinimaColors.primary)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0D0D1A))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ArrowForward, null, tint = Color(0xFF0D0D1A), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MinimaColors.surfaceContainerLow)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MinimaColors.onSurfaceVariant)
    }
}
