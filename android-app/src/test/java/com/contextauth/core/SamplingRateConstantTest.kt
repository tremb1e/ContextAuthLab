package com.contextauth.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SamplingRateConstantTest {
    @Test
    fun samplingRateIsFixedAt100Hz() {
        assertEquals(100, SamplingConfig.SAMPLING_RATE_HZ)
        assertEquals(10_000, SamplingConfig.SAMPLING_PERIOD_US)
        assertEquals(200_000, SamplingConfig.MAX_REPORT_LATENCY_US)
    }
}
