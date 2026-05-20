package com.contextauth.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureQueuePolicyTest {
    @Test
    fun backoffCapsAtFiveMinutesAndDeadLettersAfterTwentyRetries() {
        assertEquals(5_000, FailureQueuePolicy.cappedBackoffMillis(0))
        assertEquals(10_000, FailureQueuePolicy.cappedBackoffMillis(1))
        assertEquals(20_000, FailureQueuePolicy.cappedBackoffMillis(2))
        assertEquals(300_000, FailureQueuePolicy.cappedBackoffMillis(10))
        assertEquals(150_000, FailureQueuePolicy.fullJitterDelayMillis(10, 0.5))
        assertFalse(FailureQueuePolicy.shouldDeadLetter(19))
        assertTrue(FailureQueuePolicy.shouldDeadLetter(20))
    }
}
