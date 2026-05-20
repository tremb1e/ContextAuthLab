package com.contextauth.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenGestureDetectorTest {
    @Test
    fun sevenFastTapsTrigger() {
        val detector = HiddenGestureDetector()
        var triggered = false
        repeat(7) { triggered = detector.recordTap(1_000 + it * 100L) }
        assertTrue(triggered)
    }

    @Test
    fun slowTapGapResets() {
        val detector = HiddenGestureDetector()
        repeat(6) { detector.recordTap(1_000 + it * 100L) }
        assertFalse(detector.recordTap(10_000))
    }

    @Test
    fun longPressTriggers() {
        assertTrue(HiddenGestureDetector().recordPress(3_000))
    }
}
