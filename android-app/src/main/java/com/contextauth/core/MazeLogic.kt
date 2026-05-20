package com.contextauth.core

data class MazeWall(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class MazePoint(
    val x: Float,
    val y: Float
)

object MazeLogic {
    const val BALL_RADIUS = 0.045f
    val walls = listOf(
        MazeWall(0.18f, 0.00f, 0.23f, 0.56f),
        MazeWall(0.40f, 0.32f, 0.45f, 1.00f),
        MazeWall(0.64f, 0.00f, 0.69f, 0.58f),
        MazeWall(0.69f, 0.68f, 0.90f, 0.73f)
    )
    val exit = MazeWall(0.84f, 0.78f, 0.98f, 0.96f)

    fun move(current: MazePoint, attempted: MazePoint): MazePoint {
        val bounded = MazePoint(
            attempted.x.coerceIn(BALL_RADIUS, 1f - BALL_RADIUS),
            attempted.y.coerceIn(BALL_RADIUS, 1f - BALL_RADIUS)
        )
        val xOnly = MazePoint(bounded.x, current.y)
        val afterX = if (collides(xOnly)) current else xOnly
        val yOnly = MazePoint(afterX.x, bounded.y)
        return if (collides(yOnly)) afterX else yOnly
    }

    fun isAtExit(point: MazePoint): Boolean =
        point.x in exit.left..exit.right && point.y in exit.top..exit.bottom

    fun collides(point: MazePoint): Boolean = walls.any { wall ->
        point.x + BALL_RADIUS > wall.left &&
            point.x - BALL_RADIUS < wall.right &&
            point.y + BALL_RADIUS > wall.top &&
            point.y - BALL_RADIUS < wall.bottom
    }
}
