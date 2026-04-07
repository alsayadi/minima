package com.minima.os.capability.alarm

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.minima.os.capability.registry.CapabilityProvider
import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.StepResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmCapability @Inject constructor(
    @ApplicationContext private val context: Context
) : CapabilityProvider {

    override val id = "alarm"
    override fun supportedActions() = listOf("set_alarm", "set_timer")

    override suspend fun execute(step: ActionStep): StepResult = when (step.action) {
        "set_alarm" -> setAlarm(step.params)
        "set_timer" -> setTimer(step.params)
        else -> StepResult(success = false, error = "Unknown action: ${step.action}")
    }

    private fun setAlarm(params: Map<String, String>): StepResult {
        val timeStr = params["time"] ?: return StepResult(false, error = "No time specified")
        val (hour, minute) = parseTime(timeStr) ?: return StepResult(
            false, error = "Couldn't parse time '$timeStr'"
        )
        val label = params["label"] ?: params["description"] ?: "Alarm"
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val hh = "%02d".format(hour); val mm = "%02d".format(minute)
            StepResult(true, data = mapOf("answer" to "Alarm set for $hh:$mm", "time" to "$hh:$mm"))
        } catch (e: Exception) {
            StepResult(false, error = "Alarm failed: ${e.message}")
        }
    }

    private fun setTimer(params: Map<String, String>): StepResult {
        val seconds = params["seconds"]?.toIntOrNull()
            ?: params["minutes"]?.toIntOrNull()?.let { it * 60 }
            ?: return StepResult(false, error = "No duration")
        val label = params["label"] ?: "Timer"
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            StepResult(true, data = mapOf(
                "answer" to "Timer set for ${seconds / 60} min ${seconds % 60} sec",
                "seconds" to seconds.toString()
            ))
        } catch (e: Exception) {
            StepResult(false, error = "Timer failed: ${e.message}")
        }
    }

    private fun parseTime(s: String): Pair<Int, Int>? {
        val t = s.lowercase().trim()
        // "7am", "7:30pm", "14:00", "9"
        val re = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$")
        val m = re.find(t) ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val mm = m.groupValues[2].toIntOrNull() ?: 0
        val ampm = m.groupValues[3]
        if (ampm == "pm" && h < 12) h += 12
        if (ampm == "am" && h == 12) h = 0
        if (h !in 0..23 || mm !in 0..59) return null
        return h to mm
    }
}
