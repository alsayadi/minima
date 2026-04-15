package com.minima.os.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per completed task — the OODA loop's "outcome" unit. */
@Entity(tableName = "task_outcomes")
data class TaskOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val command: String,
    val intent: String,           // IntentType.name
    val confidence: String,       // Confidence.name
    val provider: String,         // Provider.name (OPENAI, GROQ, ...) or "NONE"
    val model: String = "",
    val classificationMs: Long = 0,
    val executionMs: Long = 0,
    val totalMs: Long = 0,
    val success: Boolean = true,
    val voiceInitiated: Boolean = false,
    val llmFallbackUsed: Boolean = false,
    val hourOfDay: Int = 0,
    val dayOfWeek: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

/** Every time the OODA loop applies (or proposes) a change, one row here. */
@Entity(tableName = "tuning_changes")
data class TuningChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,               // which batch produced this proposal
    val param: String,               // name of the tunable
    val previousValue: String,
    val proposedValue: String,
    val reason: String,              // diagnosis problem
    val suggestion: String,          // diagnosis suggestion
    val applied: Boolean = false,    // true = actually changed, false = proposal only
    val timestamp: Long = System.currentTimeMillis(),
    /** Success rate at the moment this change was applied (null if not applied yet). */
    val baselineSuccess: Double? = null,
    /** Post-apply delta in percentage points vs baseline (null until next batch runs). */
    val attributionPp: Int? = null
)
