package com.minima.os

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minima.os.ui.theme.MinimaColors
import com.minima.os.ui.theme.MinimaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receives ACTION_SEND from any app. Shows a bottom sheet with quick actions
 * (summarize / translate / explain / save memory / ask anything), then forwards
 * the command into LauncherActivity where the normal command pipeline runs it
 * and the result appears in the task feed.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shared = extractSharedText(intent)
        if (shared.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            MinimaTheme {
                ShareSheet(
                    sharedText = shared,
                    onAction = { prompt ->
                        val combined = buildPrompt(prompt, shared)
                        // Single execution path: forward the command to LauncherActivity.
                        // Its ViewModel collects from PendingCommandBus and submits, so
                        // the task lands in the feed with proper classify/plan/execute
                        // state — rather than being fired twice (once here, once there).
                        openLauncherWithPrefill(combined)
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun extractSharedText(i: Intent?): String? {
        if (i == null) return null
        return when (i.action) {
            Intent.ACTION_SEND -> {
                val text = i.getStringExtra(Intent.EXTRA_TEXT)
                val subject = i.getStringExtra(Intent.EXTRA_SUBJECT)
                listOfNotNull(subject?.takeIf { it.isNotBlank() }, text).joinToString("\n").trim()
                    .takeIf { it.isNotBlank() }
            }
            Intent.ACTION_PROCESS_TEXT -> {
                // Text selection toolbar → Minima
                i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }

    private fun buildPrompt(actionVerb: String, body: String): String {
        // If the user picked a quick action, prefix it. Otherwise use body as-is.
        return if (actionVerb.isBlank()) body
        else "$actionVerb: $body"
    }

    private fun openLauncherWithPrefill(command: String) {
        runCatching {
            val i = Intent(this, LauncherActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SHARED_COMMAND, command)
            }
            startActivity(i)
        }
    }

    companion object {
        const val EXTRA_SHARED_COMMAND = "com.minima.os.EXTRA_SHARED_COMMAND"
    }
}

@Composable
private fun ShareSheet(
    sharedText: String,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var customPrompt by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val kb = LocalSoftwareKeyboardController.current

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
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MinimaColors.surface)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MinimaColors.outline.copy(alpha = 0.4f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(14.dp))

            Text(
                "Share with Minima",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MinimaColors.onSurface
            )

            Spacer(Modifier.height(10.dp))

            // Preview of the shared payload
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MinimaColors.surfaceContainerLow)
                    .padding(12.dp)
            ) {
                Text(
                    text = sharedText,
                    fontSize = 12.sp,
                    color = MinimaColors.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            ActionRow(
                Icons.Outlined.Summarize,
                "Summarize",
                "Short, clear recap"
            ) { onAction("Summarize the following in 3 bullet points") }

            ActionRow(
                Icons.Outlined.Language,
                "Translate",
                "Detect language, translate to English"
            ) { onAction("Translate the following to English, preserving tone") }

            ActionRow(
                Icons.Outlined.Lightbulb,
                "Explain simply",
                "ELI5 — like I'm five"
            ) { onAction("Explain the following in plain, simple language") }

            ActionRow(
                Icons.Outlined.AutoFixHigh,
                "Rewrite cleaner",
                "Polish grammar + tone"
            ) { onAction("Rewrite the following more clearly, keeping the meaning") }

            ActionRow(
                Icons.Outlined.Bookmark,
                "Save as memory",
                "Minima remembers this"
            ) { onAction("Remember this") }

            ActionRow(
                Icons.Outlined.Chat,
                "Ask anything",
                "Type your own question"
            ) { /* handled below */ }

            Spacer(Modifier.height(12.dp))

            // Custom prompt field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MinimaColors.surfaceContainerLow)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (customPrompt.isBlank()) {
                        Text(
                            "Ask Minima about this…",
                            fontSize = 13.sp,
                            color = MinimaColors.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    BasicTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        textStyle = TextStyle(
                            color = MinimaColors.onSurface,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(MinimaColors.primary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (customPrompt.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MinimaColors.primary.copy(alpha = 0.2f))
                            .clickable {
                                focus.clearFocus()
                                kb?.hide()
                                onAction(customPrompt.trim())
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Send, "Send",
                            tint = MinimaColors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MinimaColors.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MinimaColors.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MinimaColors.onSurface)
            Text(subtitle, fontSize = 11.sp, color = MinimaColors.onSurfaceVariant)
        }
    }
}
