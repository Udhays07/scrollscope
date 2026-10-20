package com.scrollscope.app.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Writes the structure of the current screen as text: class, view ID, bounds and
 * flags. Text content is deliberately left out; descriptions are truncated.
 */
object TreeDumper {
    private const val MAX_DEPTH = 45

    fun dump(root: AccessibilityNodeInfo?): String {
        if (root == null) return "No active window."
        val sb = StringBuilder()
        sb.append("package=").append(root.packageName).append('\n')
        walk(root, 0, sb)
        return sb.toString()
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, sb: StringBuilder) {
        if (depth > MAX_DEPTH) return
        val r = Rect().also { node.getBoundsInScreen(it) }
        sb.append("  ".repeat(depth))
            .append(node.className?.toString()?.substringAfterLast('.') ?: "?")
        node.viewIdResourceName?.let { sb.append("  id=").append(it.substringAfter(":id/")) }
        sb.append("  [").append(r.left).append(',').append(r.top).append(' ')
            .append(r.width()).append('x').append(r.height()).append(']')
        if (node.isScrollable) sb.append("  SCROLLABLE")
        if (!node.isVisibleToUser) sb.append("  hidden")
        node.contentDescription?.let { sb.append("  desc=\"").append(it.toString().take(24)).append('"') }
        sb.append('\n')
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walk(it, depth + 1, sb) }
        }
    }
}
