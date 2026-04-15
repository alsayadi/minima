package com.minima.os.ui.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.minima.os.ui.theme.MinimaColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 9 vertical bars that scale with audio RMS level while listening.
 * Phase-offset sine wave gives the bars life even when silent — so the
 * user knows the mic is open.
 */
@Composable
fun VoiceWaveform(
    rms: Float,                          // 0..1
    modifier: Modifier = Modifier,
    barCount: Int = 9,
    barColor: Color = MinimaColors.primary
) {
    val infinite = rememberInfiniteTransition(label = "voice-wave")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    // Smooth rms spikes so the visualization doesn't jitter
    val smoothedRms by animateFloatAsState(
        targetValue = rms.coerceIn(0f, 1f),
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "rms"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val gap = 4f
        val barWidth = (w - gap * (barCount - 1)) / barCount
        for (i in 0 until barCount) {
            val offsetPhase = phase + i * 0.6f
            val sineComponent = (sin(offsetPhase) * 0.5f + 0.5f)  // 0..1
            val baseline = 0.15f + 0.10f * sineComponent  // ambient idle "breathing"
            val intensity = (baseline + smoothedRms * (0.85f - baseline * 0.5f)).coerceIn(0f, 1f)
            val barHeight = h * intensity
            val x = i * (barWidth + gap)
            val y = (h - barHeight) / 2f
            drawRoundRect(
                color = barColor.copy(alpha = 0.45f + 0.55f * abs(sineComponent)),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
