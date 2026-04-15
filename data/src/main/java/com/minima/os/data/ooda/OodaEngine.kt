package com.minima.os.data.ooda

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.minima.os.data.dao.OutcomeDao
import com.minima.os.data.dao.TuningChangeDao
import com.minima.os.data.entity.TaskOutcomeEntity
import com.minima.os.data.entity.TuningChangeEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generalized OODA loop for Minima — ported from the supermeme trading bot's
 * autoresearch.ts. Every [MIN_BATCH_SIZE] new outcomes:
 *   1. Observe — load the new outcomes.
 *   2. Orient — group by intent / provider / voiceInitiated, compute stats.
 *   3. Decide — run priority-ordered diagnose(), return the FIRST match.
 *   4. Act    — (log-only by default) persist a TuningChangeEntity proposal.
 *
 * Iron laws: one change per batch, worst bucket first, never auto-apply
 * destructive changes, track every change in the changelog.
 */
@Singleton
class OodaEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outcomeDao: OutcomeDao,
    private val changeDao: TuningChangeDao
) {

    companion object {
        private const val TAG = "OodaEngine"
        const val MIN_BATCH_SIZE = 30
        private const val PREFS = "minima_ooda"
        private const val KEY_LAST_BATCH_AT = "last_batch_at"
        private const val KEY_BATCH_ID = "batch_id"
        // Apply modes: LOG_ONLY (default) | AUTO_SAFE | HUMAN_QUEUE
        private const val KEY_APPLY_MODE = "apply_mode"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class BatchStats(
        val batchId: Long,
        val count: Int,
        val successRate: Double,
        val avgTotalMs: Long,
        val voiceFailRate: Double,
        val byIntent: Map<String, BucketStats>,
        val byProvider: Map<String, BucketStats>,
        val byVoiceText: Map<String, BucketStats>
    )

    data class BucketStats(
        val count: Int,
        val successRate: Double,
        val avgMs: Long
    )

    data class Diagnosis(
        val problem: String,
        val suggestion: String,
        val param: String,
        val previousValue: String,
        val proposedValue: String
    )

    /**
     * Count-based trigger: called after every TaskOutcome is logged.
     * If enough new outcomes have accumulated, runs the full loop and
     * returns the diagnosis (or null if below MIN_BATCH_SIZE).
     */
    suspend fun maybeRunBatch(): Diagnosis? {
        val lastBatchAt = prefs.getLong(KEY_LAST_BATCH_AT, 0L)
        val newCount = outcomeDao.countSince(lastBatchAt)
        if (newCount < MIN_BATCH_SIZE) return null

        val batchId = prefs.getLong(KEY_BATCH_ID, 0L) + 1
        val outcomes = outcomeDao.getSince(lastBatchAt)
        Log.i(TAG, "Batch $batchId: analyzing ${outcomes.size} outcomes")

        val stats = analyze(outcomes, batchId)
        val diagnosis = diagnose(stats, outcomes)

        if (diagnosis != null) {
            Log.i(TAG, "Batch $batchId diagnosis: ${diagnosis.problem} -> ${diagnosis.suggestion}")
            changeDao.insert(
                TuningChangeEntity(
                    batchId = batchId,
                    param = diagnosis.param,
                    previousValue = diagnosis.previousValue,
                    proposedValue = diagnosis.proposedValue,
                    reason = diagnosis.problem,
                    suggestion = diagnosis.suggestion,
                    applied = false  // LOG_ONLY for now
                )
            )
        }

        prefs.edit()
            .putLong(KEY_LAST_BATCH_AT, System.currentTimeMillis())
            .putLong(KEY_BATCH_ID, batchId)
            .apply()

        return diagnosis
    }

    /** Compute stats + dimension groupings. */
    private fun analyze(outcomes: List<TaskOutcomeEntity>, batchId: Long): BatchStats {
        val total = outcomes.size
        val successCount = outcomes.count { it.success }
        val avgMs = if (total > 0) outcomes.sumOf { it.totalMs } / total else 0L
        val voice = outcomes.filter { it.voiceInitiated }
        val voiceFail = if (voice.isNotEmpty())
            voice.count { !it.success }.toDouble() / voice.size else 0.0

        fun bucketize(groups: Map<String, List<TaskOutcomeEntity>>): Map<String, BucketStats> =
            groups.mapValues { (_, rows) ->
                BucketStats(
                    count = rows.size,
                    successRate = rows.count { it.success }.toDouble() / rows.size,
                    avgMs = rows.sumOf { it.totalMs } / rows.size.coerceAtLeast(1)
                )
            }

        return BatchStats(
            batchId = batchId,
            count = total,
            successRate = if (total > 0) successCount.toDouble() / total else 0.0,
            avgTotalMs = avgMs,
            voiceFailRate = voiceFail,
            byIntent = bucketize(outcomes.groupBy { it.intent }),
            byProvider = bucketize(outcomes.groupBy { it.provider }),
            byVoiceText = bucketize(outcomes.groupBy { if (it.voiceInitiated) "voice" else "text" })
        )
    }

    /**
     * Priority-ordered diagnosis. FIRST matching rule wins — one change per batch.
     * Thresholds are tuned for Minima's context (phone launcher, ~dozens of tasks/day).
     */
    private fun diagnose(stats: BatchStats, outcomes: List<TaskOutcomeEntity>): Diagnosis? {
        // Rule 1: voice bucket failing hard
        val voiceBucket = stats.byVoiceText["voice"]
        if (voiceBucket != null && voiceBucket.count >= 10 && voiceBucket.successRate < 0.75) {
            val currentTimeout = prefs.getInt("voice_timeout_ms", 3000).toString()
            val proposed = (prefs.getInt("voice_timeout_ms", 3000) + 500).coerceAtMost(5000).toString()
            return Diagnosis(
                problem = "Voice tasks succeed only ${(voiceBucket.successRate * 100).toInt()}% of the time",
                suggestion = "Extend STT silence tolerance; users need more pause",
                param = "voice_timeout_ms",
                previousValue = currentTimeout,
                proposedValue = proposed
            )
        }

        // Rule 2: an intent has catastrophic failure rate
        val worstIntent = stats.byIntent
            .filter { it.value.count >= 5 }
            .minByOrNull { it.value.successRate }
        if (worstIntent != null && worstIntent.value.successRate < 0.60) {
            return Diagnosis(
                problem = "Intent ${worstIntent.key} fails ${((1 - worstIntent.value.successRate) * 100).toInt()}% of the time (n=${worstIntent.value.count})",
                suggestion = "Review capability handler for ${worstIntent.key}; log errorMessage column for pattern",
                param = "intent_handler_${worstIntent.key}",
                previousValue = "current",
                proposedValue = "review"
            )
        }

        // Rule 3: a provider underperforms vs the rest
        val providerStats = stats.byProvider.filter { it.value.count >= 5 && it.key != "NONE" }
        if (providerStats.size >= 2) {
            val best = providerStats.maxByOrNull { it.value.successRate } ?: return null
            val worst = providerStats.minByOrNull { it.value.successRate } ?: return null
            if (best.value.successRate - worst.value.successRate > 0.20) {
                return Diagnosis(
                    problem = "${worst.key} success rate ${(worst.value.successRate * 100).toInt()}% vs ${best.key} at ${(best.value.successRate * 100).toInt()}%",
                    suggestion = "Route more traffic to ${best.key} as default",
                    param = "provider_default",
                    previousValue = worst.key,
                    proposedValue = best.key
                )
            }
        }

        // Rule 4: latency spike
        if (stats.avgTotalMs > 6000) {
            val slowestIntent = stats.byIntent
                .filter { it.value.count >= 5 }
                .maxByOrNull { it.value.avgMs }
            if (slowestIntent != null && slowestIntent.value.avgMs > 5000) {
                return Diagnosis(
                    problem = "Intent ${slowestIntent.key} averages ${slowestIntent.value.avgMs}ms (batch avg ${stats.avgTotalMs}ms)",
                    suggestion = "Skip LLM rewrite for this intent; speak capability result directly",
                    param = "llm_rewrite_skip_${slowestIntent.key}",
                    previousValue = "false",
                    proposedValue = "true"
                )
            }
        }

        // Rule 5: low LLM confidence on majority of tasks
        val lowConf = outcomes.count { it.confidence == "LOW" }.toDouble() / outcomes.size.coerceAtLeast(1)
        if (lowConf > 0.30) {
            return Diagnosis(
                problem = "${(lowConf * 100).toInt()}% of classifications have LOW confidence",
                suggestion = "Add more few-shot examples to the classifier prompt",
                param = "classifier_examples",
                previousValue = "current",
                proposedValue = "expand"
            )
        }

        // Everything is fine — but we still log the stats
        Log.i(TAG, "Batch ${stats.batchId}: no diagnosis needed (success=${(stats.successRate * 100).toInt()}%, avg=${stats.avgTotalMs}ms)")
        return null
    }

    /** For the dashboard — fast summary of recent state. */
    suspend fun currentSummary(): Summary {
        val lastBatchAt = prefs.getLong(KEY_LAST_BATCH_AT, 0L)
        val newCount = outcomeDao.countSince(lastBatchAt)
        val total = outcomeDao.countAll()
        val recentOutcomes = outcomeDao.getRecent(100)
        val stats = analyze(recentOutcomes, prefs.getLong(KEY_BATCH_ID, 0L))
        val recentChanges = changeDao.getRecent(10)
        return Summary(
            totalOutcomes = total,
            outcomesUntilNextBatch = (MIN_BATCH_SIZE - newCount).coerceAtLeast(0),
            lastBatchAt = lastBatchAt,
            stats = stats,
            recentChanges = recentChanges
        )
    }

    data class Summary(
        val totalOutcomes: Int,
        val outcomesUntilNextBatch: Int,
        val lastBatchAt: Long,
        val stats: BatchStats,
        val recentChanges: List<TuningChangeEntity>
    )
}
