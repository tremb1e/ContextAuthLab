package com.contextauth.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockSyncMathTest {
    @Test
    fun offsetFormulaUsesRoundTripMidpoint() {
        assertEquals(50, ClockSyncMath.offsetMillis(t0Millis = 1_000, t1Millis = 1_100, serverTimeMillis = 1_100))
    }

    @Test
    fun driftPpmUsesSixtySecondInterval() {
        assertEquals(100.0, ClockSyncMath.driftPpm(10, 16, previousSynced = true), 0.0001)
        assertEquals(0.0, ClockSyncMath.driftPpm(10, 16, previousSynced = false), 0.0001)
    }

    @Test
    fun ntpTimestampConvertsFrom1900Epoch() {
        val buffer = ByteArray(48)
        val seconds1970 = 2_208_988_800L
        buffer[40] = ((seconds1970 ushr 24) and 0xff).toByte()
        buffer[41] = ((seconds1970 ushr 16) and 0xff).toByte()
        buffer[42] = ((seconds1970 ushr 8) and 0xff).toByte()
        buffer[43] = (seconds1970 and 0xff).toByte()

        assertEquals(0L, NtpClient.readTimestampMillis(buffer, 40))
    }
}
