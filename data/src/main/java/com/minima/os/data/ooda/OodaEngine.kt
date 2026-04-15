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

    enum class ApplyMode { LOG_ONLY, AUTO_SAFE, HUMAN_QUEUE }

    companion object {
        private const val TAG = "OodaEngine"
        const val MIN_BATCH_SIZE = 30
        private const val PREFS = "minima_ooda"
        private const val KEY_LAST_BATCH_AT = "last_batch_at"
        private const val KEY_BATCH_ID = "batch_id"
        const val KEY_APPLY_MODE = "apply_mode"
        const val KEY_APPLIED_PREFIX = "applied_"  // applied_voice_timeout_ms = "4500"
        const val KEY_LAST_APPLIED_PARAM = "last_applied_param"
        const val KEY_LAST_APPLIED_PREV = "last_applied_prev"
        const val KEY_LAST_APPLIED_BASELINE_SUCCESS = "last_applied_baseline_success"  // double as bits
        const val KEY_LAST_APPLIED_BATCH_ID = "last_applied_batch_id"
        const val KEY_BATCH_HISTORY_JSON = "batch_history_json"
        /** Regression threshold: if new success rate drops this many percentage points below baseline, roll back. */
        private const val ROLLBACK_REGRESSION_DELTA = 0.10  // 10 pp
        /** Keep last N batches in the trend history. */
        private const val MAX_HISTORY = 50

        /** Params that are safe to auto-apply when in AUTO_SAFE mode. */
        private val SAFE_PARAMS = setOf(
            "voice_timeout_ms",
            "provider_default",
            "temperature",
            "llm_rewrite_skip_intents"
        )

        /** Params where we accept the proposal only if the delta is within range. */
        private fun isSafeDelta(param: String, prev: String, next: String): Boolean = when (param) {
            "voice_timeout_ms" -> {
                val p = prev.toIntOrNull() ?: return false
                val n = next.toIntOrNull() ?: return false
                kotlin.math.abs(n - p) <= 1500  // max 1.5s jump per batch
            }
            "provider_default" -> prev != next && next.isNotBlank()
            "temperature" -> {
                val p = prev.toDoubleOrNull() ?: return false
                val n = next.toDoubleOrNull() ?: return false
                n in 0.0..2.0 && kotlin.math.abs(n - p) <= 0.2  // gentle nudge
            }
            "llm_rewrite_skip_intents" -> next.isNotBlank()  // CSV list, always safe to replace
            else -> false
        }
    }

    /** Live feed of un-applied proposal count — drives the home-screen badge. */
    fun observePendingProposals() = changeDao.observePendingCount()

    /** Force a batch run regardless of count — used by the dashboard's "Run now" button. */
    suspend fun runBatchNow(): Diagnosis? {
        val lastBatchAt = prefs.getLong(KEY_LAST_BATCH_AT, 0L)
        val newCount = outcomeDao.countSince(lastBatchAt)
        if (newCount == 0) return null  // nothing to analyze
        val batchId = prefs.getLong(KEY_BATCH_ID, 0L) + 1
        val outcomes = outcomeDao.getSince(lastBatchAt)
        val stats = analyze(outcomes, batchId)
        val diagnosis = diagnose(stats, outcomes)
        if (diagnosis != null) {
            changeDao.insert(
                TuningChangeEntity(
                    batchId = batchId,
                    param = diagnosis.param,
                    previousValue = diagnosis.previousValue,
                    proposedValue = diagnosis.proposedValue,
                    reason = diagnosis.problem,
                    suggestion = diagnosis.suggestion,
                    applied = false
                )
            )
        }
        appendBatchHistory(stats.successRate, stats.avgTotalMs, stats.count)
        prefs.edit()
            .putLong(KEY_LAST_BATCH_AT, System.currentTimeMillis())
            .putLong(KEY_BATCH_ID, batchId)
            .apply()
        return diagnosis
    }

    /** Inject synthetic outcomes for demos / smoke tests. */
    suspend fun seedSyntheticOutcomes(count: Int = 35): Int {
        val now = System.currentTimeMillis()
        val intents = listOf("OPEN_APP", "GET_WEATHER", "ANSWER", "SET_REMINDER", "CALL_CONTACT", "FLASHLIGHT")
        val providers = listOf("OPENAI", "GROQ", "ANTHROPIC")
        repeat(count) { i ->
            val isVoice = i % 3 == 0
            // Simulate voice failing more often — so Rule 1 fires
            val voiceFail = isVoice && (i % 7 == 0 || i % 5 == 0)
            outcomeDao.insert(
                TaskOutcomeEntity(
                    taskId = "seed-${now}-$i",
                    command = "seed command $i",
                    intent = intents[i % intents.size],
                    confidence = if (i % 4 == 0) "LOW" else "HIGH",
                    provider = providers[i % providers.size],
                    model = "seed",
                    classificationMs = 600L + (i * 13 % 400),
                    executionMs = 1200L + (i * 17 % 1600),
                    totalMs = 1800L + (i * 31 % 2000),
                    success = !voiceFail && i % 23 != 0,
                    voiceInitiated = isVoice,
                    hourOfDay = (8 + i) % 24,
                    dayOfWeek = 1 + (i % 7),
                    timestamp = now - ((count - i) * 60_000L)
                )
            )
        }
        return count
    }

    fun applyMode(): ApplyMode {
        val v = prefs.getString(KEY_APPLY_MODE, ApplyMode.LOG_ONLY.name) ?: return ApplyMode.LOG_ONLY
        return runCatching { ApplyMode.valueOf(v) }.getOrDefault(ApplyMode.LOG_ONLY)
    }

    fun setApplyMode(mode: ApplyMode) {
        prefs.edit().putString(KEY_APPLY_MODE, mode.name).apply()
    }

    /** Read the last-applied value for a param (null if never applied). */
    fun appliedValue(param: String): String? =
        prefs.getString(KEY_APPLIED_PREFIX + param, null)

    /** Mark a proposal as applied: flip the row's flag, store the value for runtime lookup. */
    suspend fun applyProposal(change: TuningChangeEntity) {
        prefs.edit()
            .putString(KEY_APPLIED_PREFIX + change.param, change.proposedValue)
            .apply()
        // Re-insert with applied=true so dashboard reflects it
        changeDao.insert(change.copy(id = 0, applied = true, timestamp = System.currentTimeMillis()))
        Log.i(TAG, "Applied ${change.param}: ${change.previousValue} -> ${change.proposedValue}")
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

    object Rules {
        /**
         * Pure diagnosis function — no Android deps, fully testable.
         * Priority-ordered: first matching rule wins (one change per batch).
         * @param appliedValues map of current values for tunable params (what runtime is using).
         */
        fun diagnosePure(
            stats: BatchStats,
            outcomes: List<TaskOutcomeEntity>,
            appliedValues: Map<String, String>,
            /** Recent tuning history (newest first). Drives Rule 11 — anti-oscillation. */
            recentChanges: List<TuningChangeEntity> = emptyList()
        ): Diagnosis? {
            // Rule 11 (priority 0): don't re-propose a param that's been flip-flopping.
            // If within the last 4 applied+rollback rows the same param appeared ≥3 times,
            // it's oscillating — skip it for this batch.
            val oscillating = recentChanges.take(6)
                .groupBy { it.param }
                .filter { it.value.size >= 3 }
                .keys
            // Rule 1: voice bucket failing hard
            val voiceBucket = stats.byVoiceText["voice"]
            if (voiceBucket != null && voiceBucket.count >= 10 && voiceBucket.successRate < 0.75
                && "voice_timeout_ms" !in oscillating) {
                val currentTimeout = appliedValues["voice_timeout_ms"]?.toIntOrNull() ?: 3000
                val proposed = (currentTimeout + 500).coerceAtMost(5000)
                return Diagnosis(
                    problem = "Voice tasks succeed only ${(voiceBucket.successRate * 100).toInt()}% of the time",
                    suggestion = "Extend STT silence tolerance; users need more pause",
                    param = "voice_timeout_ms",
                    previousValue = currentTimeout.toString(),
                    proposedValue = proposed.toString()
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
            // Rule 3: provider underperforms
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
            // Rule 4: latency spike — extend skip list
            if (stats.avgTotalMs > 6000) {
                val slowest = stats.byIntent.filter { it.value.count >= 5 }.maxByOrNull { it.value.avgMs }
                if (slowest != null && slowest.value.avgMs > 5000) {
                    val currentSkip = appliedValues["llm_rewrite_skip_intents"] ?: ""
                    val set = currentSkip.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                    if (slowest.key !in set) {
                        set.add(slowest.key)
                        return Diagnosis(
                            problem = "Intent ${slowest.key} averages ${slowest.value.avgMs}ms (batch avg ${stats.avgTotalMs}ms)",
                            suggestion = "Skip LLM rewrite for ${slowest.key}",
                            param = "llm_rewrite_skip_intents",
                            previousValue = currentSkip,
                            proposedValue = set.joinToString(",")
                        )
                    }
                }
            }
            // Rule 5: low confidence — bump temperature
            val lowConf = outcomes.count { it.confidence == "LOW" }.toDouble() / outcomes.size.coerceAtLeast(1)
            if (lowConf > 0.30 && "temperature" !in oscillating) {
                val t = appliedValues["temperature"]?.toDoubleOrNull() ?: 0.3
                val proposed = (t + 0.1).coerceIn(0.1, 0.7)
                if (kotlin.math.abs(proposed - t) > 0.001) {
                    return Diagnosis(
                        problem = "${(lowConf * 100).toInt()}% of classifications have LOW confidence",
                        suggestion = "Nudge temperature up",
                        param = "temperature",
                        previousValue = String.format(java.util.Locale.US, "%.1f", t),
                        proposedValue = String.format(java.util.Locale.US, "%.1f", proposed)
                    )
                }
            }
            // Rule 6: system stable — drop temperature
            val highConf = outcomes.count { it.confidence == "HIGH" }.toDouble() / outcomes.size.coerceAtLeast(1)
            if (highConf > 0.90 && stats.avgTotalMs < 3000 && stats.successRate > 0.95
                && "temperature" !in oscillating) {
                val t = appliedValues["temperature"]?.toDoubleOrNull() ?: 0.3
                val proposed = (t - 0.1).coerceIn(0.1, 0.7)
                if (kotlin.math.abs(proposed - t) > 0.001 && t > 0.15) {
                    return Diagnosis(
                        problem = "${(highConf * 100).toInt()}% HIGH confidence, system is stable",
                        suggestion = "Drop temperature for determinism",
                        param = "temperature",
                        previousValue = String.format(java.util.Locale.US, "%.1f", t),
                        proposedValue = String.format(java.util.Locale.US, "%.1f", proposed)
                    )
                }
            }
            // Rule 7: slow voice intent → add to skip list
            val voiceByIntent = outcomes.filter { it.voiceInitiated }
                .groupBy { it.intent }
                .mapValues { (_, rows) ->
                    BucketStats(
                        count = rows.size,
                        successRate = rows.count { it.success }.toDouble() / rows.size,
                        avgMs = rows.sumOf { it.totalMs } / rows.size.coerceAtLeast(1)
                    )
                }
                .filter { it.value.count >= 5 && it.value.avgMs > 4000 }
            val worstVoice = voiceByIntent.maxByOrNull { it.value.avgMs }
            if (worstVoice != null) {
                val currentSkip = appliedValues["llm_rewrite_skip_intents"] ?: ""
                val set = currentSkip.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
                if (worstVoice.key !in set) {
                    set.add(worstVoice.key)
                    return Diagnosis(
                        problem = "Voice ${worstVoice.key} averages ${worstVoice.value.avgMs}ms — user waits too long",
                        suggestion = "Skip LLM rewrite for voice ${worstVoice.key}",
                        param = "llm_rewrite_skip_intents",
                        previousValue = currentSkip,
                        proposedValue = set.joinToString(",")
                    )
                }
            }

            // Rule 8: error-message clustering — if ≥40% of failures share an error prefix, surface it
            val failures = outcomes.filter { !it.success && !it.errorMessage.isNullOrBlank() }
            if (failures.size >= 5) {
                val byPrefix = failures.groupBy { it.errorMessage!!.split(" ", limit = 2).first().lowercase() }
                val worstCluster = byPrefix.maxByOrNull { it.value.size }
                if (worstCluster != null && worstCluster.value.size.toDouble() / failures.size >= 0.40) {
                    val suggestion = when (worstCluster.key) {
                        "network", "timeout", "connection", "unable" -> "Enable offline fallback; disable conversation mode"
                        "mic", "permission" -> "Prompt user to re-grant permissions"
                        "parse", "invalid" -> "Tighten LLM prompt / add examples"
                        else -> "Investigate '${worstCluster.key}' error cluster"
                    }
                    return Diagnosis(
                        problem = "${worstCluster.value.size}/${failures.size} failures start with '${worstCluster.key}'",
                        suggestion = suggestion,
                        param = "error_cluster_${worstCluster.key}",
                        previousValue = "uninvestigated",
                        proposedValue = "investigate"
                    )
                }
            }

            // Rule 9: time-of-day regression — if one 4-hour bucket is much worse than the rest
            val byHour = outcomes.groupBy { it.hourOfDay / 4 }  // 0..5 = six 4-hour buckets
                .filter { it.value.size >= 5 }
                .mapValues { (_, rows) ->
                    rows.count { it.success }.toDouble() / rows.size
                }
            if (byHour.size >= 2) {
                val worstSlot = byHour.minByOrNull { it.value }!!
                val othersAvg = byHour.filter { it.key != worstSlot.key }
                    .values.average()
                if (othersAvg - worstSlot.value > 0.20) {
                    val slotRange = "${worstSlot.key * 4}:00-${worstSlot.key * 4 + 3}:59"
                    return Diagnosis(
                        problem = "Hour slot $slotRange succeeds ${(worstSlot.value * 100).toInt()}% vs ${(othersAvg * 100).toInt()}% elsewhere",
                        suggestion = "Investigate context for that time window (commute? network quality?)",
                        param = "time_slot_${worstSlot.key}",
                        previousValue = "baseline",
                        proposedValue = "regression"
                    )
                }
            }

            // Rule 10: RTL script detected in failed voice commands — propose locale switch hint
            val arabicRegex = Regex("[\\u0600-\\u06FF\\u0750-\\u077F]")  // Arabic + Arabic Supplement
            val rtlFailures = outcomes.count {
                it.voiceInitiated && !it.success && arabicRegex.containsMatchIn(it.command)
            }
            if (rtlFailures >= 3) {
                return Diagnosis(
                    problem = "$rtlFailures voice commands contain Arabic but failed",
                    suggestion = "Offer bilingual STT — set recognizer language to 'ar' or dual-locale",
                    param = "stt_language_hint",
                    previousValue = "en-US",
                    proposedValue = "ar-multilang"
                )
            }

            return null
        }
    }

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

        // Attribution — compare to baseline set when last auto-apply ran
        val lastParam = prefs.getString(KEY_LAST_APPLIED_PARAM, null)
        val lastPrev = prefs.getString(KEY_LAST_APPLIED_PREV, null)
        val baselineBits = prefs.getLong(KEY_LAST_APPLIED_BASELINE_SUCCESS, Long.MIN_VALUE)
        val lastAppliedBatch = prefs.getLong(KEY_LAST_APPLIED_BATCH_ID, -1L)
        val baselineSuccess = if (baselineBits != Long.MIN_VALUE)
            java.lang.Double.longBitsToDouble(baselineBits) else Double.NaN

        if (lastParam != null && lastAppliedBatch == batchId - 1 && !baselineSuccess.isNaN()) {
            val delta = stats.successRate - baselineSuccess
            val deltaPct = (delta * 100).toInt()
            Log.i(TAG, "Attribution for $lastParam: $deltaPct pp (baseline ${(baselineSuccess * 100).toInt()}% -> ${(stats.successRate * 100).toInt()}%)")
            if (delta < -ROLLBACK_REGRESSION_DELTA && lastPrev != null) {
                // Rollback
                prefs.edit()
                    .putString(KEY_APPLIED_PREFIX + lastParam, lastPrev)
                    .remove(KEY_LAST_APPLIED_PARAM)
                    .remove(KEY_LAST_APPLIED_PREV)
                    .remove(KEY_LAST_APPLIED_BASELINE_SUCCESS)
                    .apply()
                changeDao.insert(
                    TuningChangeEntity(
                        batchId = batchId,
                        param = lastParam,
                        previousValue = "current",
                        proposedValue = lastPrev,
                        reason = "Regression $deltaPct pp after last change — auto-rollback",
                        suggestion = "Reverted to previous value",
                        applied = true
                    )
                )
                Log.w(TAG, "Rolled back $lastParam due to $deltaPct pp regression")
            }
        }

        val diagnosis = diagnose(stats, outcomes)

        if (diagnosis != null) {
            Log.i(TAG, "Batch $batchId diagnosis: ${diagnosis.problem} -> ${diagnosis.suggestion}")
            val mode = applyMode()
            val autoApply = mode == ApplyMode.AUTO_SAFE &&
                diagnosis.param in SAFE_PARAMS &&
                isSafeDelta(diagnosis.param, diagnosis.previousValue, diagnosis.proposedValue)
            val row = TuningChangeEntity(
                batchId = batchId,
                param = diagnosis.param,
                previousValue = diagnosis.previousValue,
                proposedValue = diagnosis.proposedValue,
                reason = diagnosis.problem,
                suggestion = diagnosis.suggestion,
                applied = autoApply
            )
            changeDao.insert(row)
            if (autoApply) {
                prefs.edit()
                    .putString(KEY_APPLIED_PREFIX + diagnosis.param, diagnosis.proposedValue)
                    .putString(KEY_LAST_APPLIED_PARAM, diagnosis.param)
                    .putString(KEY_LAST_APPLIED_PREV, diagnosis.previousValue)
                    .putLong(KEY_LAST_APPLIED_BASELINE_SUCCESS,
                        java.lang.Double.doubleToRawLongBits(stats.successRate))
                    .putLong(KEY_LAST_APPLIED_BATCH_ID, batchId)
                    .apply()
                Log.i(TAG, "AUTO_SAFE applied ${diagnosis.param}: ${diagnosis.previousValue} -> ${diagnosis.proposedValue}")
            }
        }

        // Append to trend history (capped list of "successRate per batch")
        appendBatchHistory(stats.successRate, stats.avgTotalMs, stats.count)

        prefs.edit()
            .putLong(KEY_LAST_BATCH_AT, System.currentTimeMillis())
            .putLong(KEY_BATCH_ID, batchId)
            .apply()

        return diagnosis
    }

    private fun appendBatchHistory(successRate: Double, avgMs: Long, count: Int) {
        val existing = prefs.getString(KEY_BATCH_HISTORY_JSON, "[]") ?: "[]"
        // Simple JSON: [[success,avgMs,count],...]
        val entry = "[${String.format(java.util.Locale.US, "%.3f", successRate)},$avgMs,$count]"
        val inner = existing.trim().removePrefix("[").removeSuffix("]").trim()
        val merged = if (inner.isEmpty()) listOf(entry) else inner.split("],[").map {
            "[" + it.removePrefix("[").removeSuffix("]") + "]"
        } + entry
        val capped = merged.takeLast(MAX_HISTORY)
        prefs.edit().putString(KEY_BATCH_HISTORY_JSON, "[" + capped.joinToString(",") + "]").apply()
    }

    /** Parsed batch history for the sparkline. Returns empty if no batches yet. */
    fun batchHistory(): List<Triple<Double, Long, Int>> {
        val raw = prefs.getString(KEY_BATCH_HISTORY_JSON, null) ?: return emptyList()
        if (raw.length < 3) return emptyList()
        val stripped = raw.trim().removePrefix("[").removeSuffix("]").trim()
        if (stripped.isEmpty()) return emptyList()
        return stripped.split("],[").mapNotNull { part ->
            val clean = part.removePrefix("[").removeSuffix("]")
            val fields = clean.split(",")
            if (fields.size < 3) return@mapNotNull null
            val s = fields[0].toDoubleOrNull() ?: return@mapNotNull null
            val a = fields[1].toLongOrNull() ?: return@mapNotNull null
            val c = fields[2].toIntOrNull() ?: return@mapNotNull null
            Triple(s, a, c)
        }
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
    /** Instance wrapper — captures current applied values from prefs then delegates to the pure function. */
    internal suspend fun diagnose(stats: BatchStats, outcomes: List<TaskOutcomeEntity>): Diagnosis? {
        val applied = mapOf(
            "voice_timeout_ms" to (prefs.getString(KEY_APPLIED_PREFIX + "voice_timeout_ms", "3000") ?: "3000"),
            "temperature" to (prefs.getString(KEY_APPLIED_PREFIX + "temperature", "0.3") ?: "0.3"),
            "llm_rewrite_skip_intents" to (prefs.getString(
                KEY_APPLIED_PREFIX + "llm_rewrite_skip_intents",
                "GET_WEATHER,CREATE_CALENDAR_EVENT,FLASHLIGHT,SET_ALARM,OPEN_CAMERA,MUSIC_CONTROL"
            ) ?: "")
        )
        val recent = runCatching { changeDao.getRecent(10) }.getOrDefault(emptyList())
        return Rules.diagnosePure(stats, outcomes, applied, recent)
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
            recentChanges = recentChanges,
            history = batchHistory()
        )
    }

    data class Summary(
        val totalOutcomes: Int,
        val outcomesUntilNextBatch: Int,
        val lastBatchAt: Long,
        val stats: BatchStats,
        val recentChanges: List<TuningChangeEntity>,
        val history: List<Triple<Double, Long, Int>> = emptyList()
    )
}
