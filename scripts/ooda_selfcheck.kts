// Standalone OODA rule verification — a pared-down copy of OodaEngine.Rules.diagnosePure
// and the test cases from OodaRulesTest. No Android, no junit — just asserts with exit code.
// Prints a green check per passing test, red X per failure. Exits non-zero on any failure.

data class TaskOutcome(
    val intent: String,
    val confidence: String,
    val provider: String,
    val voiceInitiated: Boolean,
    val success: Boolean,
    val totalMs: Long = 2000,
    val hourOfDay: Int = 12,
    val command: String = "cmd",
    val errorMessage: String? = null
)

data class BucketStats(val count: Int, val successRate: Double, val avgMs: Long)
data class BatchStats(
    val count: Int,
    val successRate: Double,
    val avgTotalMs: Long,
    val byIntent: Map<String, BucketStats>,
    val byProvider: Map<String, BucketStats>,
    val byVoiceText: Map<String, BucketStats>
)
data class Diagnosis(
    val problem: String, val suggestion: String,
    val param: String, val previousValue: String, val proposedValue: String
)

fun bucketize(g: Map<String, List<TaskOutcome>>): Map<String, BucketStats> =
    g.mapValues { (_, rows) ->
        BucketStats(
            count = rows.size,
            successRate = rows.count { it.success }.toDouble() / rows.size.coerceAtLeast(1),
            avgMs = rows.sumOf { it.totalMs } / rows.size.coerceAtLeast(1)
        )
    }

fun stats(outcomes: List<TaskOutcome>) = BatchStats(
    count = outcomes.size,
    successRate = outcomes.count { it.success }.toDouble() / outcomes.size.coerceAtLeast(1),
    avgTotalMs = if (outcomes.isEmpty()) 0L else outcomes.sumOf { it.totalMs } / outcomes.size,
    byIntent = bucketize(outcomes.groupBy { it.intent }),
    byProvider = bucketize(outcomes.groupBy { it.provider }),
    byVoiceText = bucketize(outcomes.groupBy { if (it.voiceInitiated) "voice" else "text" })
)

// Port of OodaEngine.Rules.diagnosePure — priority ordered
fun diagnose(
    s: BatchStats,
    o: List<TaskOutcome>,
    applied: Map<String, String>,
    recentChangeParams: List<String> = emptyList()
): Diagnosis? {
    // 0 (Rule 11): anti-oscillation — skip params that have churned ≥3× in recent 6 changes
    val oscillating = recentChangeParams.take(6).groupingBy { it }.eachCount()
        .filter { it.value >= 3 }.keys

    // 1. voice failure
    val v = s.byVoiceText["voice"]
    if (v != null && v.count >= 10 && v.successRate < 0.75 && "voice_timeout_ms" !in oscillating) {
        val cur = applied["voice_timeout_ms"]?.toIntOrNull() ?: 3000
        val prop = (cur + 500).coerceAtMost(5000)
        return Diagnosis(
            "voice fail ${(v.successRate*100).toInt()}%", "bump timeout",
            "voice_timeout_ms", cur.toString(), prop.toString()
        )
    }
    // 2. intent catastrophic fail
    val worst = s.byIntent.filter { it.value.count >= 5 }.minByOrNull { it.value.successRate }
    if (worst != null && worst.value.successRate < 0.60) {
        return Diagnosis(
            "intent ${worst.key} fails", "review",
            "intent_handler_${worst.key}", "current", "review"
        )
    }
    // 3. provider gap
    val ps = s.byProvider.filter { it.value.count >= 5 && it.key != "NONE" }
    if (ps.size >= 2) {
        val best = ps.maxByOrNull { it.value.successRate }!!
        val w = ps.minByOrNull { it.value.successRate }!!
        if (best.value.successRate - w.value.successRate > 0.20) {
            return Diagnosis(
                "${w.key} vs ${best.key}", "swap",
                "provider_default", w.key, best.key
            )
        }
    }
    // 4. latency → skip list
    if (s.avgTotalMs > 6000) {
        val slow = s.byIntent.filter { it.value.count >= 5 }.maxByOrNull { it.value.avgMs }
        if (slow != null && slow.value.avgMs > 5000) {
            val cur = applied["llm_rewrite_skip_intents"] ?: ""
            val set = cur.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            if (slow.key !in set) {
                set.add(slow.key)
                return Diagnosis(
                    "slow ${slow.key}", "skip rewrite",
                    "llm_rewrite_skip_intents", cur, set.joinToString(",")
                )
            }
        }
    }
    // 5. low confidence → temp up
    val lowConf = o.count { it.confidence == "LOW" }.toDouble() / o.size.coerceAtLeast(1)
    if (lowConf > 0.30) {
        val t = applied["temperature"]?.toDoubleOrNull() ?: 0.3
        val p = (t + 0.1).coerceIn(0.1, 0.7)
        if (kotlin.math.abs(p - t) > 0.001) {
            return Diagnosis(
                "low conf ${(lowConf*100).toInt()}%", "temp up",
                "temperature", "%.1f".format(t), "%.1f".format(p)
            )
        }
    }
    // 6. system stable → temp down
    val hi = o.count { it.confidence == "HIGH" }.toDouble() / o.size.coerceAtLeast(1)
    if (hi > 0.90 && s.avgTotalMs < 3000 && s.successRate > 0.95) {
        val t = applied["temperature"]?.toDoubleOrNull() ?: 0.3
        val p = (t - 0.1).coerceIn(0.1, 0.7)
        if (kotlin.math.abs(p - t) > 0.001 && t > 0.15) {
            return Diagnosis(
                "stable", "temp down",
                "temperature", "%.1f".format(t), "%.1f".format(p)
            )
        }
    }
    // 8. error clustering
    val failures = o.filter { !it.success && !it.errorMessage.isNullOrBlank() }
    if (failures.size >= 5) {
        val byPrefix = failures.groupBy { it.errorMessage!!.split(" ", limit = 2).first().lowercase() }
        val worst = byPrefix.maxByOrNull { it.value.size }
        if (worst != null && worst.value.size.toDouble() / failures.size >= 0.40) {
            return Diagnosis(
                "${worst.value.size}/${failures.size} fail with '${worst.key}'",
                "investigate cluster",
                "error_cluster_${worst.key}", "uninvestigated", "investigate"
            )
        }
    }
    // 9. time-of-day regression
    val byHour = o.groupBy { it.hourOfDay / 4 }
        .filter { it.value.size >= 5 }
        .mapValues { (_, rows) -> rows.count { it.success }.toDouble() / rows.size }
    if (byHour.size >= 2) {
        val worstSlot = byHour.minByOrNull { it.value }!!
        val others = byHour.filter { it.key != worstSlot.key }.values.average()
        if (others - worstSlot.value > 0.20) {
            return Diagnosis(
                "slot ${worstSlot.key} bad", "investigate",
                "time_slot_${worstSlot.key}", "baseline", "regression"
            )
        }
    }
    // 10. RTL in failed voice commands
    val arabic = Regex("[\\u0600-\\u06FF\\u0750-\\u077F]")
    val rtl = o.count { it.voiceInitiated && !it.success && arabic.containsMatchIn(it.command) }
    if (rtl >= 3) {
        return Diagnosis(
            "$rtl RTL voice fails", "bilingual STT",
            "stt_language_hint", "en-US", "ar-multilang"
        )
    }
    // 12. classifier-timid: many successful LOW-confidence calls
    val successLow = o.count { it.success && it.confidence == "LOW" }
    val timid = successLow.toDouble() / o.size.coerceAtLeast(1)
    if (timid > 0.25 && s.successRate > 0.90 && "temperature" !in oscillating) {
        val t = applied["temperature"]?.toDoubleOrNull() ?: 0.3
        val p = (t - 0.1).coerceIn(0.1, 0.7)
        if (kotlin.math.abs(p - t) > 0.001 && t > 0.15) {
            return Diagnosis(
                "timid ${(timid*100).toInt()}%", "temp down",
                "temperature", "%.1f".format(t), "%.1f".format(p)
            )
        }
    }
    return null
}

fun defaultApplied() = mapOf(
    "voice_timeout_ms" to "3000",
    "temperature" to "0.3",
    "llm_rewrite_skip_intents" to "GET_WEATHER,CREATE_CALENDAR_EVENT,FLASHLIGHT,SET_ALARM,OPEN_CAMERA,MUSIC_CONTROL"
)

var failed = 0
fun check(name: String, cond: Boolean, detail: String = "") {
    if (cond) println("  ✓ $name")
    else {
        println("  ✗ $name  $detail")
        failed++
    }
}

println("OODA Rules self-check")
println("=====================")

// Test 1: healthy batch → Rule 6 fires (stable, drop temperature)
run {
    val o = List(30) { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true, 1500) }
    val d = diagnose(stats(o), o, defaultApplied())
    check("healthy batch → Rule 6 (stable, temp down)",
        d != null && d.param == "temperature" && d.proposedValue == "0.2",
        "got ${d?.param}=${d?.proposedValue}")
}

// Test 2: voice failing → Rule 1
run {
    val o = (1..15).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", true, it % 3 != 0, 2000) } +
            (1..20).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true, 2000) }
    val d = diagnose(stats(o), o, defaultApplied())
    check("voice fail → Rule 1 (voice_timeout_ms 3000→3500)",
        d != null && d.param == "voice_timeout_ms" && d.proposedValue == "3500",
        "got ${d?.param}=${d?.proposedValue}")
}

// Test 3: voice timeout cap at 5000
run {
    val o = (1..15).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", true, it % 3 != 0, 2000) }
    val applied = defaultApplied() + ("voice_timeout_ms" to "5000")
    val d = diagnose(stats(o), o, applied)
    check("voice timeout cap at 5000",
        d != null && d.proposedValue == "5000",
        "got ${d?.proposedValue}")
}

// Test 4: intent catastrophic fail
run {
    val o = (1..30).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true, 2000) } +
            (1..10).map { TaskOutcome("OPEN_APP", "HIGH", "OPENAI", false, it > 6, 2000) }
    val d = diagnose(stats(o), o, defaultApplied())
    check("intent 40% success → Rule 2",
        d != null && d.param == "intent_handler_OPEN_APP",
        "got ${d?.param}")
}

// Test 5: provider gap
run {
    val o = (1..10).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true, 2000) } +
            (1..10).map { TaskOutcome("ANSWER", "HIGH", "GROQ", false, it < 7, 2000) }
    val d = diagnose(stats(o), o, defaultApplied())
    check("provider gap → Rule 3 (swap OPENAI over GROQ)",
        d != null && d.param == "provider_default" && d.previousValue == "GROQ" && d.proposedValue == "OPENAI",
        "got ${d?.previousValue}→${d?.proposedValue}")
}

// Test 6: low confidence → temp up
run {
    val o = (1..20).map { TaskOutcome("ANSWER", "LOW", "OPENAI", false, true, 2000) } +
            (1..15).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true, 2000) }
    val d = diagnose(stats(o), o, defaultApplied())
    check("low confidence → Rule 5 (temp 0.3→0.4)",
        d != null && d.param == "temperature" && d.proposedValue == "0.4",
        "got ${d?.param}=${d?.proposedValue}")
}

// Test 7: Rule 6 not at temp floor
run {
    val o = List(30) { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true, 1500) }
    val applied = defaultApplied() + ("temperature" to "0.1")
    val d = diagnose(stats(o), o, applied)
    check("temp floor — no rule fires at 0.1", d == null, "got ${d?.param}")
}

// Test 8: priority — voice beats intent
run {
    val o = (1..15).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", true, it % 3 != 0, 2000) } +
            (1..10).map { TaskOutcome("OPEN_APP", "HIGH", "OPENAI", false, false, 2000) }
    val d = diagnose(stats(o), o, defaultApplied())
    check("priority: voice (Rule 1) beats intent fail (Rule 2)",
        d != null && d.param == "voice_timeout_ms",
        "got ${d?.param}")
}

// Test 9: slow intent → skip list (needs batch avg > 6000ms)
run {
    val o = (1..25).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true, 8000) } +
            (1..10).map { TaskOutcome("FLASHLIGHT", "HIGH", "OPENAI", false, true, 1500) }
    val d = diagnose(stats(o), o, defaultApplied())
    check("slow intent → Rule 4 (skip ANSWER)",
        d != null && d.param == "llm_rewrite_skip_intents" && d.proposedValue.contains("ANSWER"),
        "got ${d?.param}=${d?.proposedValue}")
}

// Test 10: error clustering
run {
    val o = (1..30).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true) } +
            (1..5).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, false,
                errorMessage = "Network timeout") } +
            (1..3).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, false,
                errorMessage = "Parse error") }
    val d = diagnose(stats(o), o, defaultApplied())
    check("error clustering → Rule 8 (network)",
        d != null && d.param == "error_cluster_network",
        "got ${d?.param}")
}

// Test 11: time-of-day regression
run {
    val o = (1..10).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, it > 7, hourOfDay = 7) } +
            (1..10).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true, hourOfDay = 14) } +
            (1..10).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true, hourOfDay = 20) }
    val d = diagnose(stats(o), o, defaultApplied())
    check("time-of-day regression → Rule 9 (slot 1)",
        d != null && d.param == "time_slot_1",
        "got ${d?.param}")
}

// Test 12: Arabic RTL in failed voice
run {
    val o = (1..20).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true) } +
            (1..4).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", true, false,
                command = "افتح الكاميرا") }
    val d = diagnose(stats(o), o, defaultApplied())
    check("Arabic voice fail → Rule 10 (locale hint)",
        d != null && d.param == "stt_language_hint" && d.proposedValue == "ar-multilang",
        "got ${d?.param}=${d?.proposedValue}")
}

// Test 13: anti-oscillation — Rule 11 blocks flip-flopping param
run {
    val o = (1..15).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", true, it % 3 != 0) } +
            (1..20).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true) }
    val churn = listOf("voice_timeout_ms", "voice_timeout_ms", "voice_timeout_ms")
    val d = diagnose(stats(o), o, defaultApplied(), churn)
    check("oscillation → Rule 11 blocks voice_timeout_ms",
        d == null,
        "got ${d?.param}")
}

// Test 14: classifier-timid → Rule 12 drops temperature
run {
    // Must sneak past Rule 5 (fires at lowConf > 30%) while clearing Rule 12 (timid > 25%).
    // 9 LOW + 22 HIGH = 31 outcomes. lowConf = 29% (no Rule 5). timidRate = 29% (> 25%). All success.
    val o = (1..9).map { TaskOutcome("ANSWER", "LOW", "OPENAI", false, true) } +
            (1..22).map { TaskOutcome("ANSWER", "HIGH", "OPENAI", false, true) }
    val d = diagnose(stats(o), o, defaultApplied())
    check("classifier timid → Rule 12 (temp 0.3→0.2)",
        d != null && d.param == "temperature" && d.proposedValue == "0.2",
        "got ${d?.param}=${d?.proposedValue}")
}

println("=====================")
if (failed == 0) {
    println("✅  All 14 rule tests passed — OODA diagnose engine verified")
    kotlin.system.exitProcess(0)
} else {
    println("❌  $failed test(s) failed")
    kotlin.system.exitProcess(1)
}
