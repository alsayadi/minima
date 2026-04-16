package com.minima.os.ui.commandbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minima.os.ui.theme.MinimaColors

@Composable
fun CommandBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isProcessing: Boolean,
    onAppsClick: () -> Unit,
    onVoiceClick: () -> Unit = {},
    isListening: Boolean = false,
    voiceRms: Float = 0f,
    onHistoryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Search / Apps icon (long-press → command history)
        @OptIn(ExperimentalFoundationApi::class)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (text.isBlank()) onAppsClick() else onSubmit() },
                    onLongClick = onHistoryClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MinimaColors.primary
                )
            } else {
                Icon(
                    if (text.isNotBlank()) Icons.Default.Search else Icons.Rounded.Apps,
                    contentDescription = if (text.isNotBlank()) "Search" else "Apps",
                    tint = if (text.isNotBlank()) MinimaColors.primary else Color.White.copy(alpha = 0.60f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Center: Text field OR live voice waveform while listening
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isListening) {
                com.minima.os.ui.voice.VoiceWaveform(
                    rms = voiceRms,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )
            } else {
                if (text.isEmpty() && !isProcessing) {
                    Text(
                        text = "What do you need?",
                        color = MinimaColors.onSurface.copy(alpha = 0.35f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 0.3.sp
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = TextStyle(
                        color = MinimaColors.onSurface.copy(alpha = 0.90f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(MinimaColors.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Right: Mic button (accent bg)
        IconButton(
            onClick = onVoiceClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (isListening) MinimaColors.primary.copy(alpha = 0.55f)
                    else MinimaColors.primary.copy(alpha = 0.20f)
                )
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "Voice",
                tint = if (isListening) Color.White else MinimaColors.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))
    }
}
