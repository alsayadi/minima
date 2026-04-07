package com.minima.os.capability.calendar

import android.content.ContentValues
import android.content.Context
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
            val description = params["description"] ?: return StepResult(
                success = false, error = "No event description provided"
            )

            val startTime = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1) // Default: 1 hour from now
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startTime.timeInMillis)
                put(CalendarContract.Events.DTEND, startTime.timeInMillis + 3600000) // 1hr
                put(CalendarContract.Events.TITLE, description)
                put(CalendarContract.Events.CALENDAR_ID, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

            if (uri != null) {
                StepResult(success = true, data = mapOf("eventUri" to uri.toString()))
            } else {
                StepResult(success = false, error = "Failed to create event")
            }
        } catch (e: SecurityException) {
            StepResult(success = false, error = "Calendar permission not granted")
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
