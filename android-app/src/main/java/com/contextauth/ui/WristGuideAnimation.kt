package com.contextauth.ui

import android.content.res.Configuration
import android.graphics.Paint
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contextauth.core.LocaleText
import com.contextauth.ui.theme.ContextAuthLabTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** File-private localization helper (public LocaleText API). */
private fun l(zh: String, en: String): String = LocaleText.pick(zh, en)

/** The three standardized wrist+phone motions, one per panel. */
private enum class WristScene { TRANSLATION_FRONT, ROTATION_TOP, SWING_SIDE }

/** Rotate [point] about [pivot] by [degrees] (clockwise in screen space, y-down). */
private fun rotatePoint(point: Offset, pivot: Offset, degrees: Float): Offset {
    val radians = degrees.toDouble() * PI / 180.0
    val dx = point.x - pivot.x
    val dy = point.y - pivot.y
    return Offset(
        x = pivot.x + (dx * cos(radians) - dy * sin(radians)).toFloat(),
        y = pivot.y + (dx * sin(radians) + dy * cos(radians)).toFloat(),
    )
}

/** Draw an arrow: a shaft [start]->[end] plus two head strokes. */
private fun DrawScope.drawArrow(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float,
    headLength: Float,
) {
    drawLine(color, start, end, strokeWidth = strokeWidth)
    val direction = kotlin.math.atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val spread = PI / 7.0
    val left = Offset(
        x = end.x - (headLength * cos(direction - spread)).toFloat(),
        y = end.y - (headLength * sin(direction - spread)).toFloat(),
    )
    val right = Offset(
        x = end.x - (headLength * cos(direction + spread)).toFloat(),
        y = end.y - (headLength * sin(direction + spread)).toFloat(),
    )
    drawLine(color, end, left, strokeWidth = strokeWidth)
    drawLine(color, end, right, strokeWidth = strokeWidth)
}

/**
 * Beautiful, minimal, theme-aware instructional animation for the C7 wrist task.
 * Three persistent reference panels animate at once: left-right translation (front view),
 * left-right rotation (top view), forward-back swing (side view).
 *
 * Semantic colors (no hardcoded hex): face/face-direction = tertiary (amber);
 * phone screen + screen-direction arrow = primary (blue);
 * motion trajectory = secondary (green); figure = onSurface/onSurfaceVariant.
 */
@Composable
internal fun WristGuide() {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }

    // ONE infinite transition driving all three normalized t in [-1, 1].
    val transition = rememberInfiniteTransition(label = "wristGuide")

    // Keyframes hold briefly at each extreme so the end poses are readable.
    fun holdSpec(durationMs: Int) = infiniteRepeatable<Float>(
        animation = keyframes {
            durationMillis = durationMs
            (-1f) at 0
            (-1f) at (durationMs * 0.18f).toInt() using FastOutSlowInEasing
            1f at (durationMs * 0.50f).toInt()
            1f at (durationMs * 0.68f).toInt() using FastOutSlowInEasing
            (-1f) at durationMs
        },
        repeatMode = RepeatMode.Restart,
    )

    val tTranslate by transition.animateFloat(
        initialValue = -1f, targetValue = 1f, animationSpec = holdSpec(2600), label = "tTranslate",
    )
    val tRotate by transition.animateFloat(
        initialValue = -1f, targetValue = 1f, animationSpec = holdSpec(2400), label = "tRotate",
    )
    val tSwing by transition.animateFloat(
        initialValue = -1f, targetValue = 1f, animationSpec = holdSpec(2800), label = "tSwing",
    )

    // Per-motion ranges (degrees). Static fallback parks at the +extreme.
    val angleTranslate = (if (animationsEnabled) tTranslate else 1f) * 30f
    val angleRotate = (if (animationsEnabled) tRotate else 1f) * 55f
    val angleSwing = (if (animationsEnabled) tSwing else 1f) * 28f

    // Semantic colors read in the composable body and passed into the draw.
    val cs = MaterialTheme.colorScheme
    val faceColor = cs.tertiary
    val screenColor = cs.primary
    val motionColor = cs.secondary
    val figureSoft = cs.onSurfaceVariant
    val figureStrong = cs.onSurface
    val canvasBg = cs.surface
    val canvasBorder = cs.outlineVariant
    val badgeBg = cs.onSurface.copy(alpha = 0.08f)
    val badgeText = cs.onSurfaceVariant
    val haloColor = cs.surface

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // (1) Intro line.
        Text(
            l(
                "用三张示意图演示三种手腕动作：左右平移、左右转动、前后摆动。橙色＝面部朝向，蓝色＝手机屏幕朝向，绿色＝运动轨迹。只动手腕，头保持正对前方。",
                "Three diagrams show three wrist motions: left-right translation, left-right rotation, and forward-back swing. Orange = face direction, blue = phone-screen direction, green = motion path. Move only your wrist; keep your head facing forward.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = figureSoft,
        )

        // (2) Compact legend row.
        WristGuideLegend(faceColor, screenColor, motionColor)

        // (3..5) Three panels, responsive.
        val panel1Title = l("① 左右平移", "① Left-right translation")
        val panel1Badge = l("正视图", "Front view")
        val panel1Caption = l(
            "屏幕始终正对脸，手腕带手机沿扇形左右平移，整条手臂不动。",
            "Keep the screen facing you; let the wrist slide the phone left and right along a fan; don't move your arm.",
        )
        val panel1Desc = l(
            "左右平移示意图（正视图）：手臂固定，手腕带动手机沿扇形弧线左右平移，手机屏幕始终正对使用者。",
            "Left-right translation (front view): the arm stays fixed while the wrist slides the phone left and right along a fan arc, with the screen always facing the user.",
        )

        val panel2Title = l("② 左右转动", "② Left-right rotation")
        val panel2Badge = l("俯视图", "Top view")
        val panel2Caption = l(
            "位置不动，只把手机绕自身中线左右转动，让屏幕朝向左右摆。",
            "Keep it in place; twist the phone about its own centerline so the screen faces left then right.",
        )
        val panel2Desc = l(
            "左右转动示意图（俯视图）：手机位置不动，绕自身中线左右转动，使屏幕朝向左右摆动。",
            "Left-right rotation (top view): the phone stays in place and twists about its own centerline so the screen direction swings left and right.",
        )

        val panel3Title = l("③ 前后摆动", "③ Forward-back swing")
        val panel3Badge = l("侧视图", "Side view")
        val panel3Caption = l(
            "只弯手腕，让手机顶端朝脸靠近再远离，前臂和头保持不动。",
            "Flex only the wrist so the phone's top tilts toward your face then away; keep forearm and head still.",
        )
        val panel3Desc = l(
            "前后摆动示意图（侧视图）：只弯曲手腕，让手机顶端朝脸部靠近再远离，前臂与头部保持不动。",
            "Forward-back swing (side view): flex only the wrist so the phone's top tilts toward and away from the face while the forearm and head stay still.",
        )

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 560.dp) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WristScenePanel(
                        modifier = Modifier.fillMaxWidth(),
                        scene = WristScene.TRANSLATION_FRONT, angle = angleTranslate,
                        title = panel1Title, badge = panel1Badge, caption = panel1Caption,
                        contentDescription = panel1Desc,
                        faceColor = faceColor, screenColor = screenColor, motionColor = motionColor,
                        figureSoft = figureSoft, figureStrong = figureStrong,
                        canvasBg = canvasBg, canvasBorder = canvasBorder,
                        badgeBg = badgeBg, badgeText = badgeText, haloColor = haloColor,
                    )
                    WristScenePanel(
                        modifier = Modifier.fillMaxWidth(),
                        scene = WristScene.ROTATION_TOP, angle = angleRotate,
                        title = panel2Title, badge = panel2Badge, caption = panel2Caption,
                        contentDescription = panel2Desc,
                        faceColor = faceColor, screenColor = screenColor, motionColor = motionColor,
                        figureSoft = figureSoft, figureStrong = figureStrong,
                        canvasBg = canvasBg, canvasBorder = canvasBorder,
                        badgeBg = badgeBg, badgeText = badgeText, haloColor = haloColor,
                    )
                    WristScenePanel(
                        modifier = Modifier.fillMaxWidth(),
                        scene = WristScene.SWING_SIDE, angle = angleSwing,
                        title = panel3Title, badge = panel3Badge, caption = panel3Caption,
                        contentDescription = panel3Desc,
                        faceColor = faceColor, screenColor = screenColor, motionColor = motionColor,
                        figureSoft = figureSoft, figureStrong = figureStrong,
                        canvasBg = canvasBg, canvasBorder = canvasBorder,
                        badgeBg = badgeBg, badgeText = badgeText, haloColor = haloColor,
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WristScenePanel(
                        modifier = Modifier.weight(1f),
                        scene = WristScene.TRANSLATION_FRONT, angle = angleTranslate,
                        title = panel1Title, badge = panel1Badge, caption = panel1Caption,
                        contentDescription = panel1Desc,
                        faceColor = faceColor, screenColor = screenColor, motionColor = motionColor,
                        figureSoft = figureSoft, figureStrong = figureStrong,
                        canvasBg = canvasBg, canvasBorder = canvasBorder,
                        badgeBg = badgeBg, badgeText = badgeText, haloColor = haloColor,
                    )
                    WristScenePanel(
                        modifier = Modifier.weight(1f),
                        scene = WristScene.ROTATION_TOP, angle = angleRotate,
                        title = panel2Title, badge = panel2Badge, caption = panel2Caption,
                        contentDescription = panel2Desc,
                        faceColor = faceColor, screenColor = screenColor, motionColor = motionColor,
                        figureSoft = figureSoft, figureStrong = figureStrong,
                        canvasBg = canvasBg, canvasBorder = canvasBorder,
                        badgeBg = badgeBg, badgeText = badgeText, haloColor = haloColor,
                    )
                    WristScenePanel(
                        modifier = Modifier.weight(1f),
                        scene = WristScene.SWING_SIDE, angle = angleSwing,
                        title = panel3Title, badge = panel3Badge, caption = panel3Caption,
                        contentDescription = panel3Desc,
                        faceColor = faceColor, screenColor = screenColor, motionColor = motionColor,
                        figureSoft = figureSoft, figureStrong = figureStrong,
                        canvasBg = canvasBg, canvasBorder = canvasBorder,
                        badgeBg = badgeBg, badgeText = badgeText, haloColor = haloColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun WristGuideLegend(faceColor: Color, screenColor: Color, motionColor: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WristLegendItem(faceColor, l("橙色：面部 / 面朝方向（始终不动）", "Orange: face / face direction (always fixed)"))
        WristLegendItem(screenColor, l("蓝色：手机屏幕 / 屏幕朝向", "Blue: phone screen / screen direction"))
        WristLegendItem(motionColor, l("绿色：手腕运动轨迹", "Green: wrist motion path"))
    }
}

@Composable
private fun WristLegendItem(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WristScenePanel(
    modifier: Modifier,
    scene: WristScene,
    angle: Float,
    title: String,
    badge: String,
    caption: String,
    contentDescription: String,
    faceColor: Color,
    screenColor: Color,
    motionColor: Color,
    figureSoft: Color,
    figureStrong: Color,
    canvasBg: Color,
    canvasBorder: Color,
    badgeBg: Color,
    badgeText: Color,
    haloColor: Color,
) {
    // On-canvas micro-labels (kept <= 3 per panel; chosen by importance).
    val lblScreen = l("屏幕朝向", "Screen dir.")
    val lblFace = l("面部朝向", "Face dir.")
    val lblFacePlane = l("人脸平面", "Face plane")
    val lblWrist = l("手腕", "Wrist")
    val lblTwistAxis = l("转动轴（屏幕中线）", "Twist axis")
    val lblArmFixed = l("手臂固定", "Arm fixed")
    val lblFanPath = l("扇形轨迹", "Fan path")

    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(10.dp)
            .semantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Title row: circled-number title + spacer.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, canvasBorder, RoundedCornerShape(14.dp))
                .drawWithCache {
                    // Build Paint objects ONCE per size/state, reused inside onDrawBehind.
                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 10.sp.toPx()
                        textAlign = Paint.Align.CENTER
                    }
                    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 10.sp.toPx()
                        textAlign = Paint.Align.CENTER
                        color = haloColor.toArgb()
                        style = Paint.Style.STROKE
                        strokeWidth = 1.5.dp.toPx()
                    }
                    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 10.sp.toPx()
                        textAlign = Paint.Align.CENTER
                        isFakeBoldText = true
                        color = badgeText.toArgb()
                    }

                    onDrawBehind {
                        drawWristScene(
                            scene = scene,
                            angle = angle,
                            faceColor = faceColor,
                            screenColor = screenColor,
                            motionColor = motionColor,
                            figureSoft = figureSoft,
                            figureStrong = figureStrong,
                            canvasBg = canvasBg,
                            canvasBorder = canvasBorder,
                            badgeBg = badgeBg,
                            labelPaint = labelPaint,
                            haloPaint = haloPaint,
                            badgePaint = badgePaint,
                            badge = badge,
                            lblScreen = lblScreen,
                            lblFace = lblFace,
                            lblFacePlane = lblFacePlane,
                            lblWrist = lblWrist,
                            lblTwistAxis = lblTwistAxis,
                            lblArmFixed = lblArmFixed,
                            lblFanPath = lblFanPath,
                        )
                    }
                },
        )

        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ----------------------------------------------------------------------------
// Drawing
// ----------------------------------------------------------------------------

/** Draw an on-canvas label with a [haloPaint] halo behind it for legibility. */
private fun DrawScope.drawHaloLabel(
    text: String,
    x: Float,
    y: Float,
    align: Paint.Align,
    colorArgb: Int,
    labelPaint: Paint,
    haloPaint: Paint,
) {
    val canvas = drawContext.canvas.nativeCanvas
    haloPaint.textAlign = align
    canvas.drawText(text, x, y, haloPaint)
    labelPaint.textAlign = align
    labelPaint.color = colorArgb
    canvas.drawText(text, x, y, labelPaint)
}

private fun DrawScope.drawWristScene(
    scene: WristScene,
    angle: Float,
    faceColor: Color,
    screenColor: Color,
    motionColor: Color,
    figureSoft: Color,
    figureStrong: Color,
    canvasBg: Color,
    canvasBorder: Color,
    badgeBg: Color,
    labelPaint: Paint,
    haloPaint: Paint,
    badgePaint: Paint,
    badge: String,
    lblScreen: String,
    lblFace: String,
    lblFacePlane: String,
    lblWrist: String,
    lblTwistAxis: String,
    lblArmFixed: String,
    lblFanPath: String,
) {
    val w = size.width
    val h = size.height
    val s = minOf(w, h)

    // Canvas background.
    drawRoundRect(
        color = canvasBg,
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = CornerRadius(s * 0.055f, s * 0.055f),
    )

    when (scene) {
        WristScene.TRANSLATION_FRONT -> drawTranslationFront(
            angle, faceColor, screenColor, motionColor, figureSoft, figureStrong,
            canvasBg, canvasBorder, labelPaint, haloPaint, lblScreen, lblWrist, lblArmFixed, lblFanPath,
        )
        WristScene.ROTATION_TOP -> drawRotationTop(
            angle, faceColor, screenColor, motionColor, figureSoft, figureStrong,
            canvasBg, labelPaint, haloPaint, lblScreen, lblFace, lblFacePlane, lblTwistAxis,
        )
        WristScene.SWING_SIDE -> drawSwingSide(
            angle, faceColor, screenColor, motionColor, figureSoft, figureStrong,
            canvasBg, canvasBorder, labelPaint, haloPaint, lblScreen, lblFace, lblFacePlane, lblWrist,
        )
    }

    // View-perspective badge (top-right, inside canvas).
    val padX = s * 0.030f
    val padY = s * 0.014f
    val measured = badgePaint.measureText(badge)
    val badgeW = measured + padX * 2f
    val badgeH = badgePaint.textSize + padY * 2f
    val badgeLeft = w - badgeW - s * 0.030f
    val badgeTop = s * 0.030f
    drawRoundRect(
        color = badgeBg,
        topLeft = Offset(badgeLeft, badgeTop),
        size = Size(badgeW, badgeH),
        cornerRadius = CornerRadius(s * 0.024f, s * 0.024f),
    )
    drawContext.canvas.nativeCanvas.drawText(
        badge,
        badgeLeft + badgeW / 2f,
        badgeTop + badgeH / 2f + badgePaint.textSize / 3f,
        badgePaint,
    )
}

// ---- PANEL 1 — 左右平移 / FRONT VIEW ----------------------------------------
private fun DrawScope.drawTranslationFront(
    angle: Float,
    faceColor: Color,
    screenColor: Color,
    motionColor: Color,
    figureSoft: Color,
    figureStrong: Color,
    canvasBg: Color,
    canvasBorder: Color,
    labelPaint: Paint,
    haloPaint: Paint,
    lblScreen: String,
    lblWrist: String,
    lblArmFixed: String,
    lblFanPath: String,
) {
    val w = size.width
    val h = size.height
    val s = minOf(w, h)
    val range = 30f

    val cx = 0.50f * w
    val wrist = Offset(0.50f * w, 0.80f * h)
    val arcR = s * 0.40f
    val p0 = Offset(cx, wrist.y - arcR)
    val phoneW = s * 0.15f
    val phoneH = s * 0.30f

    val figureSoftC = figureSoft.copy(alpha = 0.45f)

    // Floor line.
    drawLine(
        canvasBorder,
        Offset(0.14f * w, 0.80f * h),
        Offset(0.86f * w, 0.80f * h),
        strokeWidth = s * 0.006f,
    )

    // Head: plain circle + front ring + center dot (gaze toward viewer).
    val headCenter = Offset(cx, 0.165f * h)
    val headR = s * 0.10f
    drawCircle(figureSoftC, radius = headR, center = headCenter)
    drawCircle(figureSoftC, radius = s * 0.022f, center = headCenter, style = Stroke(width = s * 0.006f))
    drawCircle(figureSoftC, radius = s * 0.012f, center = headCenter)

    // Forearm capsule (fixed, vertical).
    val armTop = 0.42f * h
    drawRoundRect(
        color = figureSoftC,
        topLeft = Offset(cx - s * 0.085f / 2f, armTop),
        size = Size(s * 0.085f, wrist.y - armTop),
        cornerRadius = CornerRadius(s * 0.085f / 2f, s * 0.085f / 2f),
    )
    drawHaloLabel(lblArmFixed, cx + s * 0.10f, 0.46f * h, Paint.Align.LEFT, figureSoft.toArgb(), labelPaint, haloPaint)

    // Trajectory: range arc.
    drawArc(
        color = motionColor.copy(alpha = 0.30f),
        startAngle = 270f - range,
        sweepAngle = range * 2f,
        useCenter = false,
        topLeft = Offset(wrist.x - arcR, wrist.y - arcR),
        size = Size(arcR * 2f, arcR * 2f),
        style = Stroke(width = s * 0.014f),
    )
    // Endpoint dots.
    listOf(-range, range).forEach { limit ->
        drawCircle(motionColor.copy(alpha = 0.55f), radius = s * 0.018f, center = rotatePoint(p0, wrist, limit))
    }
    // Ghost phones at both extremes: the phone swings rigidly about the wrist (a true fan),
    // so each ghost is TILTED, staying collinear with the swinging hand.
    listOf(-range, range).forEach { limit ->
        rotate(degrees = limit, pivot = wrist) {
            drawRoundRect(
                color = figureStrong.copy(alpha = 0.16f),
                topLeft = Offset(p0.x - phoneW / 2f, p0.y - phoneH / 2f),
                size = Size(phoneW, phoneH),
                cornerRadius = CornerRadius(s * 0.030f, s * 0.030f),
            )
        }
    }
    // Direction arrow on the inner fan (back-and-forth sweep, kept clear of the phones).
    drawArrow(
        color = motionColor,
        start = rotatePoint(Offset(cx, wrist.y - arcR * 0.52f), wrist, -20f),
        end = rotatePoint(Offset(cx, wrist.y - arcR * 0.52f), wrist, 20f),
        strokeWidth = s * 0.012f,
        headLength = s * 0.045f,
    )

    // Active hand + phone, swinging rigidly about the wrist: the phone stays collinear with the
    // hand (fan motion) while the screen face stays toward the viewer (parallel to the face plane).
    rotate(degrees = angle, pivot = wrist) {
        // Hand: short capsule from the wrist to the lower end of the phone.
        drawLine(
            figureSoft.copy(alpha = 0.55f),
            wrist,
            Offset(p0.x, p0.y + phoneH / 2f),
            strokeWidth = s * 0.06f,
        )
        // Phone body.
        drawRoundRect(
            color = figureStrong.copy(alpha = 0.92f),
            topLeft = Offset(p0.x - phoneW / 2f, p0.y - phoneH / 2f),
            size = Size(phoneW, phoneH),
            cornerRadius = CornerRadius(s * 0.030f, s * 0.030f),
        )
        // Screen kept fully face-on (blue): the screen always faces the user, i.e. its plane
        // stays parallel to the face plane no matter how far the phone swings.
        drawRoundRect(
            color = screenColor.copy(alpha = 0.85f),
            topLeft = Offset(p0.x - s * 0.117f / 2f, p0.y - s * 0.21f / 2f),
            size = Size(s * 0.117f, s * 0.21f),
            cornerRadius = CornerRadius(s * 0.018f, s * 0.018f),
        )
    }
    // Screen-direction label, anchored beside the swinging screen.
    val screenAnchor = rotatePoint(Offset(p0.x + s * 0.10f, p0.y - phoneH * 0.34f), wrist, angle)
    drawHaloLabel(lblScreen, screenAnchor.x, screenAnchor.y, Paint.Align.LEFT, screenColor.toArgb(), labelPaint, haloPaint)

    // Wrist pivot.
    drawCircle(canvasBg, radius = s * 0.030f, center = wrist)
    drawCircle(motionColor, radius = s * 0.026f, center = wrist, style = Stroke(width = s * 0.008f))
    drawCircle(motionColor, radius = s * 0.012f, center = wrist)
    drawHaloLabel(lblWrist, wrist.x, wrist.y + s * 0.06f, Paint.Align.CENTER, motionColor.toArgb(), labelPaint, haloPaint)
}

// ---- PANEL 2 — 左右转动 / TOP-DOWN VIEW -------------------------------------
private fun DrawScope.drawRotationTop(
    angle: Float,
    faceColor: Color,
    screenColor: Color,
    motionColor: Color,
    figureSoft: Color,
    figureStrong: Color,
    canvasBg: Color,
    labelPaint: Paint,
    haloPaint: Paint,
    lblScreen: String,
    lblFace: String,
    lblFacePlane: String,
    lblTwistAxis: String,
) {
    val w = size.width
    val h = size.height
    val s = minOf(w, h)

    val cx = 0.50f * w
    val headCenter = Offset(cx, 0.165f * h)
    val headR = s * 0.105f
    val cPhone = Offset(0.50f * w, 0.62f * h) // PIVOT = bar's own center.
    val barLen = s * 0.42f
    val barThk = s * 0.075f
    val barTopY = cPhone.y - barThk / 2f

    val figureSoftC = figureSoft.copy(alpha = 0.45f)

    // Head from above: circle + downward nose wedge.
    drawCircle(figureSoftC, radius = headR, center = headCenter)
    val noseTip = Offset(cx, headCenter.y + headR * 1.05f)
    val noseL = Offset(cx - headR * 0.18f, headCenter.y + headR * 0.6f)
    val noseR = Offset(cx + headR * 0.18f, headCenter.y + headR * 0.6f)
    val nosePath = androidx.compose.ui.graphics.Path().apply {
        moveTo(noseTip.x, noseTip.y)
        lineTo(noseL.x, noseL.y)
        lineTo(noseR.x, noseR.y)
        close()
    }
    drawPath(nosePath, color = figureSoftC)

    // Face plane (fixed, amber, horizontal).
    val facePlaneY = headCenter.y + headR * 0.75f
    drawLine(
        color = faceColor.copy(alpha = 0.55f),
        start = Offset(0.16f * w, facePlaneY),
        end = Offset(0.84f * w, facePlaneY),
        strokeWidth = s * 0.012f,
    )
    drawHaloLabel(lblFacePlane, 0.16f * w, facePlaneY - s * 0.025f, Paint.Align.LEFT, faceColor.toArgb(), labelPaint, haloPaint)

    // Face direction arrow (fixed, amber, points DOWN toward phone).
    val faceArrowEnd = Offset(cx, cPhone.y - barThk * 0.5f - s * 0.06f)
    drawArrow(
        color = faceColor.copy(alpha = 0.7f),
        start = Offset(cx, facePlaneY + s * 0.02f),
        end = faceArrowEnd,
        strokeWidth = s * 0.012f,
        headLength = s * 0.045f,
    )
    drawHaloLabel(lblFace, cx + s * 0.04f, (facePlaneY + faceArrowEnd.y) / 2f, Paint.Align.LEFT, faceColor.toArgb(), labelPaint, haloPaint)

    // Forearm foreshortened (fixed, wide capsule from bottom up to phone).
    val armTopY = cPhone.y + barThk * 0.5f + s * 0.02f
    drawRoundRect(
        color = figureSoftC,
        topLeft = Offset(cx - s * 0.13f / 2f, armTopY),
        size = Size(s * 0.13f, h - armTopY),
        cornerRadius = CornerRadius(s * 0.065f, s * 0.065f),
    )

    // Trajectory of the screen normal (fan), centered on cPhone.
    val normalR = s * 0.20f
    drawArc(
        color = motionColor.copy(alpha = 0.30f),
        startAngle = 270f - 55f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = Offset(cPhone.x - normalR, cPhone.y - normalR),
        size = Size(normalR * 2f, normalR * 2f),
        style = Stroke(width = s * 0.014f),
    )
    val normalBase = Offset(cPhone.x, cPhone.y - normalR)
    listOf(-55f, 55f).forEach { limit ->
        drawCircle(motionColor.copy(alpha = 0.55f), radius = s * 0.018f, center = rotatePoint(normalBase, cPhone, limit))
    }

    // GHOST bars at +/-55 via rotate about cPhone.
    listOf(-55f, 55f).forEach { limit ->
        rotate(degrees = limit, pivot = cPhone) {
            drawRoundRect(
                color = figureStrong.copy(alpha = 0.16f),
                topLeft = Offset(cPhone.x - barLen / 2f, cPhone.y - barThk / 2f),
                size = Size(barLen, barThk),
                cornerRadius = CornerRadius(s * 0.02f, s * 0.02f),
            )
        }
    }

    // Active bar (TWIST about own center).
    rotate(degrees = angle, pivot = cPhone) {
        drawRoundRect(
            color = figureStrong.copy(alpha = 0.92f),
            topLeft = Offset(cPhone.x - barLen / 2f, cPhone.y - barThk / 2f),
            size = Size(barLen, barThk),
            cornerRadius = CornerRadius(s * 0.02f, s * 0.02f),
        )
        // Screen edge: bold line along the bar's TOP long edge.
        val edgeY = cPhone.y - barThk / 2f
        drawLine(
            color = screenColor,
            start = Offset(cPhone.x - barLen / 2f + s * 0.01f, edgeY),
            end = Offset(cPhone.x + barLen / 2f - s * 0.01f, edgeY),
            strokeWidth = s * 0.018f,
        )
        // Screen-normal arrow: from top-edge midpoint, perpendicular, length S*0.15.
        drawArrow(
            color = screenColor,
            start = Offset(cPhone.x, edgeY - s * 0.02f),
            end = Offset(cPhone.x, edgeY - s * 0.15f),
            strokeWidth = s * 0.018f,
            headLength = s * 0.045f,
        )
    }

    // Screen-direction label follows the rotating bar.
    val screenAnchor = rotatePoint(Offset(cPhone.x + s * 0.09f, barTopY - s * 0.10f), cPhone, angle)
    drawHaloLabel(lblScreen, screenAnchor.x, screenAnchor.y, Paint.Align.LEFT, screenColor.toArgb(), labelPaint, haloPaint)

    // Pivot dot at cPhone (twist axis = screen centerline).
    drawCircle(canvasBg, radius = s * 0.026f, center = cPhone)
    drawCircle(motionColor, radius = s * 0.022f, center = cPhone, style = Stroke(width = s * 0.008f))
    drawCircle(motionColor, radius = s * 0.011f, center = cPhone)
    drawHaloLabel(lblTwistAxis, cPhone.x, cPhone.y + s * 0.06f, Paint.Align.CENTER, motionColor.toArgb(), labelPaint, haloPaint)
}

// ---- PANEL 3 — 前后摆动 / SIDE VIEW -----------------------------------------
private fun DrawScope.drawSwingSide(
    angle: Float,
    faceColor: Color,
    screenColor: Color,
    motionColor: Color,
    figureSoft: Color,
    figureStrong: Color,
    canvasBg: Color,
    canvasBorder: Color,
    labelPaint: Paint,
    haloPaint: Paint,
    lblScreen: String,
    lblFace: String,
    lblFacePlane: String,
    lblWrist: String,
) {
    val w = size.width
    val h = size.height
    val s = minOf(w, h)
    val range = 28f

    val headCenter = Offset(0.28f * w, 0.34f * h)
    val headR = s * 0.115f
    val facePlaneX = headCenter.x + headR * 0.65f
    val wrist = Offset(0.66f * w, 0.80f * h) // PIVOT = wrist (correct here).
    val arcR = s * 0.40f
    val phoneThk = s * 0.085f
    val phoneLen = s * 0.46f
    val phoneCenterY = wrist.y - phoneLen / 2f - s * 0.05f
    val screenEdgeX = wrist.x - phoneThk / 2f - s * 0.006f

    val figureSoftC = figureSoft.copy(alpha = 0.45f)

    // Floor line.
    drawLine(
        canvasBorder,
        Offset(0.06f * w, 0.80f * h),
        Offset(0.94f * w, 0.80f * h),
        strokeWidth = s * 0.006f,
    )

    // Head profile: circle + rightward nose wedge.
    drawCircle(figureSoftC, radius = headR, center = headCenter)
    val noseTip = Offset(headCenter.x + headR * 1.08f, headCenter.y + headR * 0.1f)
    val noseTop = Offset(headCenter.x + headR * 0.78f, headCenter.y - headR * 0.18f)
    val noseBot = Offset(headCenter.x + headR * 0.78f, headCenter.y + headR * 0.34f)
    val nosePath = androidx.compose.ui.graphics.Path().apply {
        moveTo(noseTip.x, noseTip.y)
        lineTo(noseTop.x, noseTop.y)
        lineTo(noseBot.x, noseBot.y)
        close()
    }
    drawPath(nosePath, color = figureSoftC)

    // Face plane (fixed, amber, vertical).
    drawLine(
        color = faceColor.copy(alpha = 0.55f),
        start = Offset(facePlaneX, headCenter.y - headR * 1.35f),
        end = Offset(facePlaneX, headCenter.y + headR * 1.65f),
        strokeWidth = s * 0.012f,
    )
    drawHaloLabel(lblFacePlane, facePlaneX, headCenter.y - headR * 1.50f, Paint.Align.CENTER, faceColor.toArgb(), labelPaint, haloPaint)

    // Face direction arrow (fixed, amber, points RIGHT).
    val faceArrowEnd = Offset(wrist.x - phoneThk / 2f - s * 0.12f, headCenter.y + headR * 0.05f)
    drawArrow(
        color = faceColor.copy(alpha = 0.7f),
        start = Offset(facePlaneX + s * 0.02f, headCenter.y + headR * 0.05f),
        end = faceArrowEnd,
        strokeWidth = s * 0.012f,
        headLength = s * 0.045f,
    )
    drawHaloLabel(lblFace, (facePlaneX + faceArrowEnd.x) / 2f, headCenter.y - headR * 0.20f, Paint.Align.CENTER, faceColor.toArgb(), labelPaint, haloPaint)

    // Forearm (fixed, vertical capsule below wrist).
    drawRoundRect(
        color = figureSoftC,
        topLeft = Offset(wrist.x - s * 0.13f / 2f, wrist.y),
        size = Size(s * 0.13f, h - wrist.y + s * 0.06f),
        cornerRadius = CornerRadius(s * 0.065f, s * 0.065f),
    )

    // Trajectory: range arc about wrist.
    drawArc(
        color = motionColor.copy(alpha = 0.30f),
        startAngle = 270f - range,
        sweepAngle = range * 2f,
        useCenter = false,
        topLeft = Offset(wrist.x - arcR, wrist.y - arcR),
        size = Size(arcR * 2f, arcR * 2f),
        style = Stroke(width = s * 0.014f),
    )
    val arcBase = Offset(wrist.x, wrist.y - arcR)
    listOf(-range, range).forEach { limit ->
        drawCircle(motionColor.copy(alpha = 0.55f), radius = s * 0.018f, center = rotatePoint(arcBase, wrist, limit))
    }

    // Phone edge-on slab drawer (rotates about WRIST).
    fun drawPhoneSlab(alpha: Float, withArrow: Boolean) {
        // Phone body.
        drawRoundRect(
            color = figureStrong.copy(alpha = 0.92f * alpha),
            topLeft = Offset(wrist.x - phoneThk / 2f, phoneCenterY - phoneLen / 2f),
            size = Size(phoneThk, phoneLen),
            cornerRadius = CornerRadius(s * 0.02f, s * 0.02f),
        )
        // Screen edge: bold vertical line on the LEFT/face-side long edge.
        drawLine(
            color = screenColor.copy(alpha = alpha),
            start = Offset(screenEdgeX, phoneCenterY - phoneLen / 2f + s * 0.014f),
            end = Offset(screenEdgeX, phoneCenterY + phoneLen / 2f - s * 0.014f),
            strokeWidth = s * 0.018f,
        )
        if (withArrow) {
            // Screen-normal arrow: from screen-edge midpoint pointing LEFT toward face.
            drawArrow(
                color = screenColor.copy(alpha = alpha),
                start = Offset(screenEdgeX - s * 0.02f, phoneCenterY),
                end = Offset(screenEdgeX - s * 0.15f, phoneCenterY),
                strokeWidth = s * 0.018f,
                headLength = s * 0.045f,
            )
            // Hand: short capsule wrapping slab bottom just above wrist.
            drawRoundRect(
                color = figureSoft.copy(alpha = 0.55f),
                topLeft = Offset(wrist.x - s * 0.09f / 2f, wrist.y - s * 0.13f),
                size = Size(s * 0.09f, s * 0.14f),
                cornerRadius = CornerRadius(s * 0.045f, s * 0.045f),
            )
        }
    }

    // GHOST phones at +/-28 via rotate about wrist.
    listOf(-range, range).forEach { limit ->
        rotate(degrees = limit, pivot = wrist) { drawPhoneSlab(alpha = 0.16f, withArrow = false) }
    }
    // Active phone.
    rotate(degrees = angle, pivot = wrist) { drawPhoneSlab(alpha = 1f, withArrow = true) }

    // Screen-direction label follows the rotating arrow.
    val screenAnchor = rotatePoint(Offset(screenEdgeX - s * 0.165f, phoneCenterY - s * 0.03f), wrist, angle)
    drawHaloLabel(lblScreen, screenAnchor.x, screenAnchor.y, Paint.Align.CENTER, screenColor.toArgb(), labelPaint, haloPaint)

    // Wrist pivot.
    drawCircle(canvasBg, radius = s * 0.030f, center = wrist)
    drawCircle(motionColor, radius = s * 0.026f, center = wrist, style = Stroke(width = s * 0.008f))
    drawCircle(motionColor, radius = s * 0.012f, center = wrist)
    drawHaloLabel(lblWrist, wrist.x, wrist.y + s * 0.06f, Paint.Align.CENTER, motionColor.toArgb(), labelPaint, haloPaint)
}

// ----------------------------------------------------------------------------
// Android Studio previews — render all three panels without building/installing.
// (Infinite animations render statically at their initial pose in previews.)
// ----------------------------------------------------------------------------

@Preview(name = "Wrist guide · light", showBackground = true, widthDp = 380, heightDp = 960)
@Composable
private fun WristGuidePreviewLight() {
    ContextAuthLabTheme {
        Surface {
            Box(Modifier.padding(16.dp)) { WristGuide() }
        }
    }
}

@Preview(
    name = "Wrist guide · dark",
    showBackground = true,
    widthDp = 380,
    heightDp = 960,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun WristGuidePreviewDark() {
    ContextAuthLabTheme {
        Surface {
            Box(Modifier.padding(16.dp)) { WristGuide() }
        }
    }
}
