package com.contextauth.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.contextauth.core.AccessibilityCollectionGate
import com.contextauth.core.AccessibilityEventBus
import com.contextauth.core.CoarseOrientation
import com.contextauth.core.ContextEventSnapshot
import com.contextauth.core.RawNodeSnapshot
import com.contextauth.core.RedactionEngine
import com.contextauth.core.RedactionSummary
import com.contextauth.core.TouchEventBus
import com.contextauth.core.TouchEventSnapshot

class ResearchAccessibilityService : AccessibilityService() {
    private val redactionEngine = RedactionEngine()
    private val lastProcessedAt = mutableMapOf<Int, Long>()
    private var lastForegroundTarget = ForegroundTarget()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching {
            handleAccessibilityEvent(event)
        }
    }

    private fun handleAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!AccessibilityCollectionGate.active) return
        if (isGlobalTouchEvent(event.eventType)) {
            emitGlobalTouchEvent(event)
            return
        }
        if (!shouldProcess(event.eventType)) return
        val inputMethodVisible = isInputMethodVisible()
        val foregroundTarget = foregroundTarget(event)
        val packageName = foregroundTarget.appPackageName
        val eventType = typeName(event.eventType)
        val eventWallTime = eventWallTime(event)
        val summary = RedactionSummary()
        val roots = mutableListOf<com.contextauth.core.NodeSnapshot>()
        collectApplicationRoots(packageName, roots, summary)
        AccessibilityEventBus.emit(
            ContextEventSnapshot(
                eventType = eventType,
                eventTimeWallMillis = eventWallTime,
                appPackageName = packageName,
                foregroundActivityClassName = foregroundTarget.activityClassName,
                foregroundComponentName = foregroundTarget.componentName,
                inputMethodVisible = inputMethodVisible,
                coarseOrientation = currentCoarseOrientation(),
                windowTitleRedacted = redactedEventText(event, summary),
                rootNodes = roots.take(MAX_NODES_PER_EVENT),
                redactionSummary = summary.asMap()
            )
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        AccessibilityCollectionGate.active = false
        super.onDestroy()
    }

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
            visibleToUser = node.isVisibleToUser,
            longClickable = node.isLongClickable,
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
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "TYPE_WINDOWS_CHANGED"
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "TYPE_VIEW_SCROLLED"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
        AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "TYPE_VIEW_LONG_CLICKED"
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> "TYPE_VIEW_FOCUSED"
        AccessibilityEvent.TYPE_VIEW_SELECTED -> "TYPE_VIEW_SELECTED"
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
        AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> "TYPE_TOUCH_INTERACTION_START"
        AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> "TYPE_TOUCH_INTERACTION_END"
        else -> "TYPE_$type"
    }

    private fun isGlobalTouchEvent(type: Int): Boolean =
        type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START ||
            type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END

    private fun emitGlobalTouchEvent(event: AccessibilityEvent) {
        val eventType = when (event.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> "TOUCH_INTERACTION_START"
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> "TOUCH_INTERACTION_END"
            else -> return
        }
        TouchEventBus.emit(
            TouchEventSnapshot(
                eventType = eventType,
                eventTimeUptimeMillis = event.eventTime,
                eventTimeWallMillis = eventWallTime(event),
                collectedAtWallMillis = System.currentTimeMillis()
            )
        )
    }

    private fun eventWallTime(event: AccessibilityEvent): Long {
        val nowWall = System.currentTimeMillis()
        val eventUptime = event.eventTime
        if (eventUptime <= 0L) return nowWall
        val ageMillis = (SystemClock.uptimeMillis() - eventUptime).coerceAtLeast(0L)
        return nowWall - ageMillis
    }

    private fun summarizeActions(node: AccessibilityNodeInfo): List<String> = buildList {
        if (node.isClickable) add("CLICK")
        if (node.isLongClickable) add("LONG_CLICK")
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
        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return false
        val minIntervalMs = when (type) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> 120L
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> 180L
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> 250L
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

    private fun isInputMethodVisible(): Boolean =
        windows?.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } == true

    private fun collectApplicationRoots(
        foregroundPackageName: String?,
        out: MutableList<com.contextauth.core.NodeSnapshot>,
        summary: RedactionSummary
    ) {
        var collectedFromApplicationWindow = false
        windows?.forEachIndexed { index, window ->
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@forEachIndexed
            val root = window.root ?: return@forEachIndexed
            try {
                val rootPackageName = root.packageName?.toString()
                if (!belongsToForeground(rootPackageName, foregroundPackageName)) return@forEachIndexed
                traverse(root, rootPackageName ?: foregroundPackageName, index, out, summary)
                collectedFromApplicationWindow = true
            } finally {
                root.recycle()
            }
        }
        if (collectedFromApplicationWindow) return

        rootInActiveWindow?.let { root ->
            try {
                val rootPackageName = root.packageName?.toString()
                if (belongsToForeground(rootPackageName, foregroundPackageName)) {
                    traverse(root, rootPackageName ?: foregroundPackageName, 0, out, summary)
                }
            } finally {
                root.recycle()
            }
        }
    }

    private fun belongsToForeground(rootPackageName: String?, foregroundPackageName: String?): Boolean {
        if (foregroundPackageName.isNullOrBlank() || rootPackageName.isNullOrBlank()) return true
        return rootPackageName == foregroundPackageName
    }

    private fun foregroundTarget(event: AccessibilityEvent): ForegroundTarget {
        val appPackageName = activeApplicationWindowPackage()
            ?: event.packageName?.toString()?.takeIf { it.isNotBlank() }
            ?: lastForegroundTarget.appPackageName
        val candidateActivity = event.className?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED }
            ?.takeIf { event.packageName?.toString() == appPackageName }
        val activity = candidateActivity ?: lastForegroundTarget.activityClassName.takeIf {
            appPackageName != null && lastForegroundTarget.appPackageName == appPackageName
        }
        val component = componentName(appPackageName, activity)
            ?: lastForegroundTarget.componentName.takeIf {
                appPackageName != null && lastForegroundTarget.appPackageName == appPackageName
            }
        return ForegroundTarget(appPackageName, activity, component).also {
            if (!it.appPackageName.isNullOrBlank()) lastForegroundTarget = it
        }
    }

    private fun activeApplicationWindowPackage(): String? {
        val applicationWindows = windows?.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } ?: return null
        applicationWindows.firstOrNull { it.isActive }?.let { windowRootPackage(it) }?.let { return it }
        applicationWindows.firstOrNull { it.isFocused }?.let { windowRootPackage(it) }?.let { return it }
        applicationWindows.forEach { windowRootPackage(it)?.let { packageName -> return packageName } }
        return null
    }

    private fun windowRootPackage(window: AccessibilityWindowInfo): String? {
        val root = window.root ?: return null
        return try {
            root.packageName?.toString()?.takeIf { it.isNotBlank() }
        } finally {
            root.recycle()
        }
    }

    private fun componentName(packageName: String?, className: String?): String? {
        if (packageName.isNullOrBlank() || className.isNullOrBlank()) return null
        return runCatching { ComponentName(packageName, className).flattenToShortString() }.getOrNull()
    }

    private fun currentCoarseOrientation(): String =
        CoarseOrientation.fromAndroid(resources.configuration.orientation, displayRotation())

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation
        } else {
            (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation
        }

    private companion object {
        const val MAX_DEPTH = 14
        const val MAX_NODES_PER_EVENT = 320
    }

    private data class ForegroundTarget(
        val appPackageName: String? = null,
        val activityClassName: String? = null,
        val componentName: String? = null
    )
}
