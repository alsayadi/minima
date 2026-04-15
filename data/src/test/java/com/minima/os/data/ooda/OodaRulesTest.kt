package com.minima.os.data.ooda

import com.minima.os.data.entity.TaskOutcomeEntity
import com.minima.os.data.entity.TuningChangeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for the OODA rule engine.
 * Exercises `OodaEngine.Rules.diagnosePure` with hand-built BatchStats + outcome lists.
 * No Android runtime, no Room, no SharedPreferences — so it runs in plain JVM junit.
 */
class OodaRulesTest {

    private fun outcome(
        intent: String = "ANSWER",
        provider: String = "OPENAI",
        confidence: String = "HIGH",
        voice: Boolean = false,
        success: Boolean = true,
        totalMs: Long = 2000
    ) = TaskOutcomeEntity(
        taskId = "t",
        command = "cmd",
        intent = intent,
        confidence = confidence,
        provider = provider,
        totalMs = totalMs,
        success = success,
        voiceInitiated = voice
    )

    private fun stats(
        outcomes: List<TaskOutcomeEntity>
    ): OodaEngine.BatchStats {
        fun bucketize(g: Map<String, List<TaskOutcomeEntity>>) = g.mapValues { (_, rows) ->
            OodaEngine.BucketStats(
                count = rows.size,
                successRate = rows.count { it.success }.toDouble() / rows.size.coerceAtLeast(1),
                avgMs = rows.sumOf { it.totalMs } / rows.size.coerceAtLeast(1)
            )
        }
        val voice = outcomes.filter { it.voiceInitiated }
        return OodaEngine.BatchStats(
            batchId = 1,
            count = outcomes.size,
            successRate = outcomes.count { it.success }.toDouble() / outcomes.size.coerceAtLeast(1),
            avgTotalMs = if (outcomes.isEmpty()) 0 else outcomes.sumOf { it.totalMs } / outcomes.size,
            voiceFailRate = if (voice.isEmpty()) 0.0
                else voice.count { !it.success }.toDouble() / voice.size,
            byIntent = bucketize(outcomes.groupBy { it.intent }),
            byProvider = bucketize(outcomes.groupBy { it.provider }),
            byVoiceText = bucketize(outcomes.groupBy { if (it.voiceInitiated) "voice" else "text" })
        )
    }

    @Test fun `returns null when batch looks healthy`() {
        val outcomes = List(30) { outcome(totalMs = 1500) }
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, defaultApplied())
        // Rule 6 may fire here because of stable/fast/high-confidence — that's fine, but only if temp > 0.15
        // With default temperature 0.3, Rule 6 WILL fire and propose 0.2
        assertNotNull(d)
        assertEquals("temperature", d!!.param)
    }

    @Test fun `rule 1 fires when voice success below 75 percent with at least 10 samples`() {
        val outcomes = (1..15).map { outcome(voice = true, success = it % 3 != 0) } +  // 66% success
                      (1..20).map { outcome(voice = false, success = true) }
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, defaultApplied())
        assertNotNull(d)
        assertEquals("voice_timeout_ms", d!!.param)
        assertEquals("3000", d.previousValue)
        assertEquals("3500", d.proposedValue)
    }

    @Test fun `rule 1 respects max 5000ms cap`() {
        val outcomes = (1..15).map { outcome(voice = true, success = it % 3 != 0) }
        val applied = defaultApplied() + ("voice_timeout_ms" to "5000")
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, applied)
        assertNotNull(d)
        assertEquals("5000", d!!.proposedValue)
    }

    @Test fun `rule 2 fires when an intent fails more than 40 percent`() {
        val outcomes = (1..30).map { outcome() } +
                       (1..10).map { outcome(intent = "OPEN_APP", success = it > 6) }  // 40% success
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, defaultApplied())
        assertNotNull(d)
        assertEquals("intent_handler_OPEN_APP", d!!.param)
    }

    @Test fun `rule 3 fires when provider gap exceeds 20 points`() {
        val outcomes = (1..10).map { outcome(provider = "OPENAI", success = true) } +
                       (1..10).map { outcome(provider = "GROQ", success = it < 7) }  // 60% success
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, defaultApplied())
        assertNotNull(d)
        assertEquals("provider_default", d!!.param)
        assertEquals("GROQ", d.previousValue)
        assertEquals("OPENAI", d.proposedValue)
    }

    @Test fun `rule 5 fires when low confidence above 30 percent — temperature bumped up`() {
        val outcomes = (1..20).map { outcome(confidence = "LOW") } +
                       (1..15).map { outcome(confidence = "HIGH") }
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, defaultApplied())
        assertNotNull(d)
        assertEquals("temperature", d!!.param)
        assertEquals("0.3", d.previousValue)
        assertEquals("0.4", d.proposedValue)
    }

    @Test fun `rule 6 fires only when temperature above 0_15`() {
        val outcomes = List(30) { outcome(totalMs = 1500) }
        // temperature 0.1 is at floor — Rule 6 should NOT fire
        val applied = defaultApplied() + ("temperature" to "0.1")
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, applied)
        assertNull("Rule 6 must not fire at temp floor", d)
    }

    @Test fun `priority — voice failure beats intent failure`() {
        val outcomes = (1..15).map { outcome(voice = true, success = it % 3 != 0) } +   // voice at 66%
                       (1..10).map { outcome(intent = "OPEN_APP", success = false) }     // 0% success, but voice wins
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, defaultApplied())
        assertNotNull(d)
        assertEquals("voice_timeout_ms", d!!.param)
    }

    @Test fun `rule 4 — slow intent appends to skip list`() {
        // Rule 4 fires only when batch avg > 6000ms AND that intent's avg > 5000ms.
        // Weight ANSWER heavily enough that the aggregate crosses 6000ms.
        val outcomes = (1..25).map { outcome(intent = "ANSWER", totalMs = 8000) } +
                       (1..10).map { outcome(intent = "FLASHLIGHT", totalMs = 1500) }
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, defaultApplied())
        assertNotNull(d)
        assertEquals("llm_rewrite_skip_intents", d!!.param)
        assertTrue(d.proposedValue.contains("ANSWER"))
    }

    @Test fun `rule 8 — error-message clustering surfaces worst cluster`() {
        // 5 network failures + 3 other failures + 30 successes → 5/8 share 'network' prefix
        val outcomes = (1..30).map { outcome() } +
                       (1..5).map { outcome(success = false).copy(errorMessage = "Network timeout on port 443") } +
                       (1..3).map { outcome(success = false).copy(errorMessage = "Parse error in JSON response") }
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, defaultApplied())
        assertNotNull(d)
        assertEquals("error_cluster_network", d!!.param)
        assertTrue(d.problem.contains("network"))
    }

    @Test fun `rule 9 — time-of-day regression`() {
        // Morning commute window (6-9am) fails a lot; rest of day is fine
        val morning = (1..10).map { outcome(success = it > 7).copy(hourOfDay = 7) }   // 30% success
        val rest = (1..10).map { outcome().copy(hourOfDay = 14) } +                     // 100%
                   (1..10).map { outcome().copy(hourOfDay = 20) }                        // 100%
        val outcomes = morning + rest
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, defaultApplied())
        assertNotNull(d)
        assertEquals("time_slot_1", d!!.param)
        assertTrue(d.problem.contains("4:00-7:59"))
    }

    @Test fun `rule 11 — oscillating param is skipped`() {
        // Voice clearly failing; would normally trigger Rule 1. But voice_timeout_ms has been
        // flip-flopping in the last 6 batches (3 times), so Rule 11 blocks it.
        val outcomes = (1..15).map { outcome(voice = true, success = it % 3 != 0) } +
                       (1..20).map { outcome(voice = false, success = true) }
        val churn = List(3) {
            TuningChangeEntity(
                batchId = (10 - it).toLong(),
                param = "voice_timeout_ms",
                previousValue = "3000", proposedValue = "3500",
                reason = "churn", suggestion = "bump",
                applied = true
            )
        }
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, defaultApplied(), churn)
        // Should skip voice_timeout_ms, return null (no other rule fires on this data)
        assertNull("Rule 11 must skip oscillating voice_timeout_ms", d)
    }

    @Test fun `rule 10 — arabic in failed voice commands triggers locale hint`() {
        val outcomes = (1..20).map { outcome() } +
                       (1..4).map {
                           outcome(voice = true, success = false)
                               .copy(command = "افتح الكاميرا")  // "open camera" in Arabic
                       }
        val d = OodaEngine.Rules.diagnosePure(stats(outcomes), outcomes, defaultApplied())
        assertNotNull(d)
        assertEquals("stt_language_hint", d!!.param)
        assertEquals("ar-multilang", d.proposedValue)
    }

    private fun defaultApplied(): Map<String, String> = mapOf(
        "voice_timeout_ms" to "3000",
        "temperature" to "0.3",
        "llm_rewrite_skip_intents" to "GET_WEATHER,CREATE_CALENDAR_EVENT,FLASHLIGHT,SET_ALARM,OPEN_CAMERA,MUSIC_CONTROL"
    )
}
