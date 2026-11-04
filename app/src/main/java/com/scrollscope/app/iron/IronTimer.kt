package com.scrollscope.app.iron

enum class TimerStage { Calm, Low, Critical, Pass }

data class TimerReading(
    val text: String,
    val fractionLeft: Float,
    val stage: TimerStage,
    val spoken: String,
)

/** Pure countdown model for the Iron Hoot bubble. No Android dependencies. */
object IronTimer {
    /** m:ss under an hour, h:mm:ss from an hour up; partial seconds round up. */
    fun clock(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L) + 999) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun reading(leftMs: Long, limitMs: Long, passLeftMs: Long?, feedLabel: String): TimerReading {
        val left = leftMs.coerceAtLeast(0L)
        if (passLeftMs != null && passLeftMs > 0) {
            return TimerReading(
                text = clock(passLeftMs),
                fractionLeft = fraction(passLeftMs, IronRules.PASS_MS),
                stage = TimerStage.Pass,
                spoken = "Pass: ${minutes(passLeftMs)} left",
            )
        }
        val stage = when {
            left <= IronRules.WARN_MS -> TimerStage.Critical
            left <= IronRules.IRON_BUBBLE_MS -> TimerStage.Low
            else -> TimerStage.Calm
        }
        val spoken = if (left <= 0L) "No $feedLabel time left" else "${minutes(left)} of $feedLabel left"
        return TimerReading(clock(left), fraction(left, limitMs), stage, spoken)
    }

    private fun fraction(left: Long, limit: Long): Float =
        if (limit <= 0L) 0f else (left.toFloat() / limit.toFloat()).coerceIn(0f, 1f)

    private fun minutes(ms: Long): String {
        val n = (ms + 59_999) / 60_000
        return if (n == 1L) "1 minute" else "$n minutes"
    }
}
