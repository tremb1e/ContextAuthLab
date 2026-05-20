package com.contextauth.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MazePhysicsTest {
    @Test
    fun wallCollisionStopsHorizontalMotion() {
        val wall = MazeRect(0.50f, 0.0f, 0.54f, 1.0f)
        val (position, velocity) = MazePhysics.step(
            position = MazePoint(0.45f, 0.50f),
            velocity = MazeVelocity(1.0f, 0f),
            tiltX = 9.8f,
            tiltY = 0f,
            dtSeconds = 0.06f,
            walls = listOf(wall),
            radius = 0.04f
        )

        assertEquals(0f, velocity.x, 0.0001f)
        assertTrue(position.x <= 0.46f)
        assertFalse(MazePhysics.collides(position, listOf(wall), 0.04f))
    }

    @Test
    fun exitDetectionUsesExitRegion() {
        assertTrue(MazePhysics.reachedExit(MazePoint(0.86f, 0.84f)))
        assertFalse(MazePhysics.reachedExit(MazePoint(0.50f, 0.50f)))
    }
}
