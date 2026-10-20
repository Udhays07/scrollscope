package com.scrollscope.app.service

import android.view.accessibility.AccessibilityNodeInfo
import com.scrollscope.app.data.Apps

/**
 * Decides whether the active screen is the Reels / Shorts player.
 *
 * Instagram and YouTube don't publish their view IDs and they change between
 * app versions. These are starting guesses: if detection fails, open a reel,
 * use the bubble's "Dump screen tree" (or Debug > Dump in 5s), and replace the
 * IDs below with the ones you find in the dump. One-line fix per app.
 */
object ReelDetector {
    val PLAYER_IDS: Map<String, List<String>> = mapOf(
        Apps.INSTAGRAM to listOf(
            "clips_viewer_view_pager",
            "clips_viewer_pager",
            "clips_video_container",
            "clips_swipe_refresh_container",
        ),
        Apps.YOUTUBE to listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_player",
            "shorts_container",
        ),
    )

    @Volatile
    var lastMatch: String? = null
        private set

    fun isReelPlayer(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val ids = PLAYER_IDS[pkg] ?: return false
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            if (nodes.any { it.isVisibleToUser }) {
                lastMatch = id
                return true
            }
        }
        return false
    }
}
