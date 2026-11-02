package com.scrollscope.app.iron

import com.scrollscope.app.data.AppSettings
import com.scrollscope.app.data.Apps
import com.scrollscope.app.util.Format

enum class LockReason { Night, Limit }

/**
 * Iron Hoot's rules, kept free of Android so they're easy to reason about.
 * Only Reels/Shorts player time counts toward an app's limit; once the limit
 * is hit the whole app locks for the rest of the day. Outside open hours both
 * apps lock regardless, and the emergency pass doesn't apply at night.
 */
object IronRules {
    val APPS = setOf(Apps.INSTAGRAM, Apps.YOUTUBE)

    const val PASS_MS = 5 * 60_000L
    const val HOLD_MS = 10_000L
    const val WARN_MS = 5 * 60_000L
    const val IRON_BUBBLE_MS = 10 * 60_000L
    const val NIGHT_WARN_MIN = 15

    const val LIMIT_MIN = 15
    const val LIMIT_MAX = 120
    const val LIMIT_STEP = 15
    val OPEN_RANGE = 5..12
    val CLOSE_RANGE = 20..24

    fun limitMs(pkg: String, s: AppSettings): Long =
        (if (pkg == Apps.YOUTUBE) s.shortsLimitMin else s.reelsLimitMin) * 60_000L

    /** [closeHour] 24 means midnight. */
    fun isNight(minuteOfDay: Int, openHour: Int, closeHour: Int): Boolean =
        minuteOfDay < openHour * 60 || minuteOfDay >= closeHour * 60

    /** Minutes until the night lock starts, or null if it's already night. */
    fun minutesToClose(minuteOfDay: Int, s: AppSettings): Int? =
        if (isNight(minuteOfDay, s.openHour, s.closeHour)) null else s.closeHour * 60 - minuteOfDay

    fun lockReason(
        pkg: String,
        minuteOfDay: Int,
        watchedMs: Long,
        passActive: Boolean,
        s: AppSettings,
    ): LockReason? = when {
        !s.ironEnabled || pkg !in APPS -> null
        isNight(minuteOfDay, s.openHour, s.closeHour) -> LockReason.Night
        watchedMs >= limitMs(pkg, s) && !passActive -> LockReason.Limit
        else -> null
    }

    /**
     * True while Iron Hoot holds on: during the night hours, or once any app has
     * used up today's limit (the emergency pass doesn't count as time back).
     * Switching back to Hoot is refused and settings can only get stricter.
     */
    fun holdsOn(minuteOfDay: Int, snap: IronSnapshot, s: AppSettings): Boolean =
        s.ironEnabled && (isNight(minuteOfDay, s.openHour, s.closeHour) || APPS.any { snap.watched(it) >= limitMs(it, s) })

    fun appLabel(pkg: String) = if (pkg == Apps.YOUTUBE) "YouTube" else "Instagram"
    fun feedLabel(pkg: String) = if (pkg == Apps.YOUTUBE) "Shorts" else "Reels"
    fun opensAt(s: AppSettings) = Format.clock(s.openHour)
}
