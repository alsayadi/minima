// Standalone OODA rule verification — a pared-down copy of OodaEngine.Rules.diagnosePure
// and the test cases from OodaRulesTest. No Android, no junit — just asserts with exit code.
// Prints a green check per passing test, red X per failure. Exits non-zero on any failure.

data class TaskOutcome(
    val intent: String,
    val confidence: String,
    val provider: String,
    val voiceInitiated: Boolean,
    val success: Boolean,
    val totalMs: Long = 2000
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
fun diagnose(s: BatchStats, o: List<TaskOutcome>, applied: Map<String, String>): Diagnosis? {
    // 1. voice failure
    val v = s.byVoiceText["voice"]
    if (v != null && v.count >= 10 && v.successRate < 0.75) {
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

println("=====================")
if (failed == 0) {
    println("✅  All 9 rule tests passed — OODA diagnose engine verified")
    kotlin.system.exitProcess(0)
} else {
    println("❌  $failed test(s) failed")
    kotlin.system.exitProcess(1)
}
