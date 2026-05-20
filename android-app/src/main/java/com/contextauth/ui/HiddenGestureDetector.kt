package com.contextauth.ui

class HiddenGestureDetector(
    private val requiredTaps: Int = 7,
    private val maxTapIntervalMillis: Long = 800L,
    private val longPressMillis: Long = 3_000L
) {
    private var tapCount = 0
    private var lastTapAt = 0L

    fun recordTap(nowMillis: Long): Boolean {
        tapCount = if (nowMillis - lastTapAt < maxTapIntervalMillis) tapCount + 1 else 1
        lastTapAt = nowMillis
        if (tapCount >= requiredTaps) {
            tapCount = 0
            return true
        }
        return false
    }

    fun recordPress(durationMillis: Long): Boolean = durationMillis >= longPressMillis
}
