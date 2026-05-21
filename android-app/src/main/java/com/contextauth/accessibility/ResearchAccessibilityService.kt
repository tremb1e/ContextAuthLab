package com.contextauth.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.contextauth.core.AccessibilityEventBus
import com.contextauth.core.ContextEventSnapshot
import com.contextauth.core.RawNodeSnapshot
import com.contextauth.core.RedactionEngine
import com.contextauth.core.RedactionSummary
import com.contextauth.core.sha256Hex

class ResearchAccessibilityService : AccessibilityService() {
    private val redactionEngine = RedactionEngine()
    private val lastProcessedAt = mutableMapOf<Int, Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!shouldProcess(event.eventType)) return
        val packageName = event.packageName?.toString()
        val eventType = typeName(event.eventType)
        if (redactionEngine.shouldSkipPackage(packageName)) {
            AccessibilityEventBus.emit(
                ContextEventSnapshot(
                    eventType = "SKIP_EVENT",
                    eventTimeWallMillis = System.currentTimeMillis(),
                    packageNameHash = packageName?.let(::sha256Hex),
                    windowTitleRedacted = "<DROPPED>",
                    rootNodes = emptyList(),
                    redactionSummary = mapOf("sensitive_package_skip" to 1)
                )
            )
            return
        }
        val summary = RedactionSummary()
        val roots = mutableListOf<com.contextauth.core.NodeSnapshot>()
        rootInActiveWindow?.let { root ->
            try {
                traverse(root, packageName, 0, roots, summary)
            } finally {
                root.recycle()
            }
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            windows?.forEachIndexed { index, window ->
                window.root?.let { root ->
                    try {
                        traverse(root, packageName, index + 1, roots, summary)
                    } finally {
                        root.recycle()
                    }
                }
            }
        }
        AccessibilityEventBus.emit(
            ContextEventSnapshot(
                eventType = eventType,
                eventTimeWallMillis = System.currentTimeMillis(),
                packageNameHash = packageName?.let(::sha256Hex),
                windowTitleRedacted = redactedEventText(event, summary),
                rootNodes = roots.take(MAX_NODES_PER_EVENT),
                redactionSummary = summary.asMap()
            )
        )
    }

    override fun onInterrupt() = Unit

    private fun traverse(
        node: AccessibilityNodeInfo,
        packageName: String?,
        depth: Int,
        out: MutableList<com.contextauth.core.NodeSnapshot>,
        summary: RedactionSummary
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_NODES_PER_EVENT) return
        val className = node.className?.toString()
        val editable = node.isEditable ||
            className?.contains("EditText", ignoreCase = true) == true ||
            className?.contains("TextInput", ignoreCase = true) == true
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val raw = RawNodeSnapshot(
            nodeId = "${depth}_${node.hashCode()}",
            packageName = packageName,
            className = className,
            viewId = node.viewIdResourceName,
            text = node.text,
            contentDescription = node.contentDescription,
            clickable = node.isClickable,
            editable = editable,
            scrollable = node.isScrollable,
            checkable = node.isCheckable,
            checked = node.isChecked,
            enabled = node.isEnabled,
            focused = node.isFocused,
            selected = node.isSelected,
            password = node.isPassword,
            childCount = node.childCount,
            depth = depth,
            boundsGrid = mapOf(
                "left" to (rect.left / 24),
                "top" to (rect.top / 24),
                "right" to (rect.right / 24),
                "bottom" to (rect.bottom / 24)
            ),
            actionsSummary = summarizeActions(node)
        )
        redactionEngine.sanitizeNode(raw, summary)?.let(out::add)
        if (node.isPassword) return
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                try {
                    traverse(child, packageName, depth + 1, out, summary)
                } finally {
                    child.recycle()
                }
            }
        }
    }

    private fun typeName(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "TYPE_VIEW_SCROLLED"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
        else -> "TYPE_$type"
    }

    private fun summarizeActions(node: AccessibilityNodeInfo): List<String> = buildList {
        if (node.isClickable) add("CLICK")
        if (node.isScrollable) add("SCROLL")
        if (node.isCheckable) add("CHECK")
        if (node.isEditable) add("EDIT")
    }

    private fun redactedEventText(event: AccessibilityEvent, summary: RedactionSummary): String? {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (!event.text.isNullOrEmpty()) summary.droppedEditableTexts += 1
            return "<EDITABLE_TEXT_DROPPED>"
        }
        return redactionEngine.redactText(event.text?.joinToString(" "), summary)
    }

    private fun shouldProcess(type: Int): Boolean {
        val minIntervalMs = when (type) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> 120L
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> 180L
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> 80L
            else -> 0L
        }
        if (minIntervalMs == 0L) return true
        val now = System.currentTimeMillis()
        synchronized(lastProcessedAt) {
            val previous = lastProcessedAt[type] ?: 0L
            if (now - previous < minIntervalMs) return false
            lastProcessedAt[type] = now
            return true
        }
    }

    private companion object {
        const val MAX_DEPTH = 10
        const val MAX_NODES_PER_EVENT = 160
    }
}
