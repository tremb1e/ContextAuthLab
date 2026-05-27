package com.contextauth.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RuleDefaultsTest {
    @Test
    fun defaultSettingsAllowUnknownRuleHash() {
        val settings = AppSettings()

        assertEquals(RuleDefaults.BASELINE_VERSION, settings.ruleVersion)
        assertEquals(RuleDefaults.ZERO_HASH, settings.ruleHash)
        assertTrue(settings.ruleHash.matches(Regex("^[a-f0-9]{64}$")))
    }

    @Test
    fun envelopeKeepsUnknownRuleHashAsNonBlockingMetadata() {
        val batch = Batch(
            batchId = UUID.randomUUID().toString(),
            deviceId = "a".repeat(64),
            sessionId = UUID.randomUUID().toString(),
            collectionSource = CollectionSource.THIRD_PARTY_APP,
            taskCategory = null,
            taskSessionId = null,
            taskStartedAtWallMillis = null,
            taskElapsedSecondsAtBatchEnd = null,
            startedAtWallMillis = 1_000,
            endedAtWallMillis = 6_000,
            baseElapsedNanos = 123,
            sensorSamples = emptyList(),
            touchEvents = emptyList(),
            contextEvents = emptyList(),
            contextFeatures = emptyList(),
            skipEvents = emptyList()
        )

        val envelope = JsonCodec.buildEnvelope(batch, "", RuleDefaults.ZERO_HASH)

        assertEquals(RuleDefaults.BASELINE_VERSION, envelope.ruleVersion)
        assertEquals(RuleDefaults.ZERO_HASH, envelope.ruleHash)
    }
}
