package com.minima.os.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minima.os.model.provider.Provider

@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onApiKeySaved: (String) -> Unit,
    onSensitivityChanged: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("minima_prefs", Context.MODE_PRIVATE)
    var selectedProvider by remember {
        mutableStateOf(
            try { Provider.valueOf(prefs.getString("llm_provider", Provider.OPENAI.name) ?: Provider.OPENAI.name) }
            catch (_: Exception) { Provider.OPENAI }
        )
    }
    var apiKey by remember(selectedProvider) {
        mutableStateOf(
            prefs.getString("api_key_${selectedProvider.name}", null)
                ?: (if (selectedProvider == Provider.OPENAI) prefs.getString("openai_api_key", "") else "")
                ?: ""
        )
    }
    var customModel by remember(selectedProvider) {
        mutableStateOf(prefs.getString("llm_model_${selectedProvider.name}", "") ?: "")
    }
    var sensitivity by remember { mutableStateOf(prefs.getString("sensitivity", "NORMAL") ?: "NORMAL") }
    val oodaPrefs = context.getSharedPreferences("minima_ooda", Context.MODE_PRIVATE)
    var applyMode by remember { mutableStateOf(oodaPrefs.getString("apply_mode", "LOG_ONLY") ?: "LOG_ONLY") }

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

        // Sheet
        Column(
            modifier = modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI Provider selector
            Text(
                text = "AI Provider",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Grid of provider chips (2 per row)
            val providers = Provider.values().toList()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                providers.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { p ->
                            val isSel = p == selectedProvider
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSel) Color(0xFF7C6FED).copy(alpha = 0.22f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { selectedProvider = p }
                                    .padding(vertical = 10.dp, horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    p.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSel) Color(0xFF7C6FED) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // API Key input
            Text(
                text = "${selectedProvider.displayName} API Key",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (apiKey.isEmpty()) {
                    Text(
                        text = "sk-...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                BasicTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Optional custom model
            Text(
                text = "Model (optional)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (customModel.isEmpty()) {
                    Text(
                        text = selectedProvider.defaultModel,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
                BasicTextField(
                    value = customModel,
                    onValueChange = { customModel = it },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Default: ${selectedProvider.defaultModel}. Without an API key, only basic keyword matching is used.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Proactive sensitivity
            Text(
                text = "Proactive Mode",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("QUIET" to "Quiet", "NORMAL" to "Normal", "PROACTIVE" to "Proactive").forEach { (value, label) ->
                    val isSelected = sensitivity == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) Color(0xFF7C6FED).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { sensitivity = value }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF7C6FED) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (sensitivity) {
                    "QUIET" -> "Minimal interruptions. Only critical alerts."
                    "PROACTIVE" -> "Full assistant mode. Morning briefs, reminders, people nudges."
                    else -> "Balanced. Smart suggestions when relevant."
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Auto-tune (OODA loop) mode
            Text(
                text = "Auto-tune",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "LOG_ONLY" to "Log only",
                    "AUTO_SAFE" to "Auto-safe",
                    "HUMAN_QUEUE" to "Ask me"
                ).forEach { (value, label) ->
                    val isSel = applyMode == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSel) Color(0xFF7C6FED).copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { applyMode = value }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSel) Color(0xFF7C6FED) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (applyMode) {
                    "AUTO_SAFE" -> "Auto-apply small, safe parameter changes (voice timeout, provider swap if key exists)."
                    "HUMAN_QUEUE" -> "Show proposals in Auto-tune dashboard; you approve each one."
                    else -> "Observe only. Proposals logged; no changes applied."
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = {
                    prefs.edit()
                        .putString("llm_provider", selectedProvider.name)
                        .putString("api_key_${selectedProvider.name}", apiKey.trim())
                        .putString("llm_model_${selectedProvider.name}", customModel.trim())
                        .putString("sensitivity", sensitivity)
                        // legacy key for backward compat
                        .apply {
                            if (selectedProvider == Provider.OPENAI) {
                                putString("openai_api_key", apiKey.trim())
                            }
                        }
                        .apply()
                    oodaPrefs.edit().putString("apply_mode", applyMode).apply()
                    onApiKeySaved(apiKey.trim())
                    onSensitivityChanged?.invoke(sensitivity)
                    Toast.makeText(context, "Settings saved — restart app to apply", Toast.LENGTH_LONG).show()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C6FED)
                )
            ) {
                Text("Save", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
