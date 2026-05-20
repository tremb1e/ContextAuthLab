package com.contextauth.identity

import com.contextauth.core.DeviceIdProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdProviderTest {
    @Test
    fun hmacSha256IsDeterministicAndLowerHex() {
        val first = DeviceIdProvider.computeDeviceId("Continuous_Authentication", "android-id")
        val second = DeviceIdProvider.computeDeviceId("Continuous_Authentication", "android-id")
        assertEquals(first, second)
        assertTrue(first.matches(Regex("^[a-f0-9]{64}$")))
        assertFalseTraversal(first)
    }

    @Test
    fun differentSaltChangesDeviceId() {
        assertNotEquals(
            DeviceIdProvider.computeDeviceId("salt-a", "android-id"),
            DeviceIdProvider.computeDeviceId("salt-b", "android-id")
        )
    }

    private fun assertFalseTraversal(value: String) {
        assertTrue(!value.contains("/") && !value.contains(".."))
    }
}
