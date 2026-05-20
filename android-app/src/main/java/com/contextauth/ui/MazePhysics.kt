package com.contextauth.ui

import kotlin.math.max
import kotlin.math.min

data class MazePoint(val x: Float, val y: Float)
data class MazeVelocity(val x: Float, val y: Float)
data class MazeRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

object TiltMazeModel {
    const val BALL_RADIUS = 0.04f
    val start = MazePoint(0.12f, 0.14f)
    val exit = MazeRect(0.82f, 0.78f, 0.95f, 0.93f)
    val walls = listOf(
        MazeRect(0.22f, 0.00f, 0.26f, 0.58f),
        MazeRect(0.22f, 0.74f, 0.58f, 0.78f),
        MazeRect(0.42f, 0.20f, 0.46f, 0.78f),
        MazeRect(0.42f, 0.20f, 0.82f, 0.24f),
        MazeRect(0.66f, 0.40f, 0.70f, 1.00f)
    )
}

object MazePhysics {
    fun step(
        position: MazePoint,
        velocity: MazeVelocity,
        tiltX: Float,
        tiltY: Float,
        dtSeconds: Float,
        walls: List<MazeRect> = TiltMazeModel.walls,
        radius: Float = TiltMazeModel.BALL_RADIUS
    ): Pair<MazePoint, MazeVelocity> {
        val dt = dtSeconds.coerceIn(0.008f, 0.06f)
        val accelerated = MazeVelocity(
            x = (velocity.x + tiltX.coerceIn(-9.8f, 9.8f) * dt * 0.20f) * 0.88f,
            y = (velocity.y + tiltY.coerceIn(-9.8f, 9.8f) * dt * 0.20f) * 0.88f
        )
        val xCandidate = clamp(MazePoint(position.x + accelerated.x * dt, position.y), radius)
        val xAccepted = !collides(xCandidate, walls, radius)
        val afterX = if (xAccepted) xCandidate else position
        val yCandidate = clamp(MazePoint(afterX.x, afterX.y + accelerated.y * dt), radius)
        val yAccepted = !collides(yCandidate, walls, radius)
        val afterY = if (yAccepted) yCandidate else afterX
        return afterY to MazeVelocity(
            x = if (xAccepted) accelerated.x else 0f,
            y = if (yAccepted) accelerated.y else 0f
        )
    }

    fun reachedExit(position: MazePoint, exit: MazeRect = TiltMazeModel.exit): Boolean =
        position.x in exit.left..exit.right && position.y in exit.top..exit.bottom

    fun collides(position: MazePoint, walls: List<MazeRect>, radius: Float): Boolean =
        walls.any { circleIntersectsRect(position, radius, it) }

    private fun clamp(position: MazePoint, radius: Float): MazePoint = MazePoint(
        x = position.x.coerceIn(radius, 1f - radius),
        y = position.y.coerceIn(radius, 1f - radius)
    )

    private fun circleIntersectsRect(center: MazePoint, radius: Float, rect: MazeRect): Boolean {
        val closestX = min(max(center.x, rect.left), rect.right)
        val closestY = min(max(center.y, rect.top), rect.bottom)
        val dx = center.x - closestX
        val dy = center.y - closestY
        return dx * dx + dy * dy < radius * radius
    }
}
