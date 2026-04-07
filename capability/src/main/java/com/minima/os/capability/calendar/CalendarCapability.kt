package com.minima.os.capability.calendar

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.minima.os.capability.registry.CapabilityProvider
import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.StepResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarCapability @Inject constructor(
    @ApplicationContext private val context: Context
) : CapabilityProvider {

    override val id = "calendar"

    override fun supportedActions() = listOf("create_event", "read_events", "set_reminder")

    override suspend fun execute(step: ActionStep): StepResult {
        return when (step.action) {
            "create_event" -> createEvent(step.params)
            "read_events" -> readEvents(step.params)
            "set_reminder" -> setReminder(step.params)
            else -> StepResult(success = false, error = "Unknown action: ${step.action}")
        }
    }

    private fun createEvent(params: Map<String, String>): StepResult {
        return try {
            val title = params["title"] ?: params["description"] ?: return StepResult(
                success = false, error = "No event title provided"
            )
            val hoursFromNow = params["hours"]?.toIntOrNull() ?: 1
            val durationMin = params["duration"]?.toIntOrNull() ?: 60

            val start = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, hoursFromNow) }
            val end = (start.clone() as Calendar).apply { add(Calendar.MINUTE, durationMin) }

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.timeInMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.timeInMillis)
                params["location"]?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            StepResult(success = true, data = mapOf("answer" to "Opening calendar to create '$title'"))
        } catch (e: Exception) {
            StepResult(success = false, error = "Failed to open calendar: ${e.message}")
        }
    }

    private fun readEvents(params: Map<String, String>): StepResult {
        return try {
            val now = System.currentTimeMillis()
            val endOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
            }.timeInMillis

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND
            )

            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(now.toString(), endOfDay.toString())

            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection, selection, selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )

            val events = mutableListOf<String>()
            cursor?.use {
                while (it.moveToNext()) {
                    val title = it.getString(1) ?: "No title"
                    events.add(title)
                }
            }

            StepResult(
                success = true,
                data = mapOf(
                    "count" to events.size.toString(),
                    "events" to events.joinToString(", ")
                )
            )
        } catch (e: SecurityException) {
            StepResult(success = false, error = "Calendar permission not granted")
        }
    }

    private fun setReminder(params: Map<String, String>): StepResult {
        val description = params["description"] ?: return StepResult(
            success = false, error = "No reminder description"
        )

        // For now, create a calendar event as a reminder
        return createEvent(params + ("description" to "Reminder: $description"))
    }
}
