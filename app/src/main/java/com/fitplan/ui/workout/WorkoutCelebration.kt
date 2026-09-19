package com.fitplan.ui.workout

import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 一个动作的全部组都做完时放的礼花。
 *
 * 用 [Dialog] 承载：`AlertDialog`（例如「要不要提高目标」）是独立 window，
 * 只有让礼花也成为一个**更晚创建**的 window 才能画在它之上。
 * 因此这里先等 [CELEBRATION_DELAY_MILLIS] 再挂载，并且给窗口加 `FLAG_NOT_TOUCHABLE` /
 * `FLAG_NOT_FOCUSABLE`，让它只负责显示，不吃触摸也不抢焦点，礼花期间照常能操作下面的弹窗。
 *
 * [tick] 每自增一次表示新放一轮礼花；为 0 表示还没放过。
 */
@Composable
internal fun WorkoutCelebrationOverlay(tick: Int) {
    var playingTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        if (tick <= 0) return@LaunchedEffect
        // 同一时刻弹出的 AlertDialog 会先建窗口，晚一步挂载礼花才能盖在它上面。
        delay(CELEBRATION_DELAY_MILLIS)
        playingTick = tick
    }

    if (playingTick > 0) {
        CelebrationDialog(tick = playingTick, onFinished = { playingTick = 0 })
    }
}

@Composable
private fun CelebrationDialog(tick: Int, onFinished: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            // 纯装饰层：不接收触摸、不抢焦点，也不给底下压一层遮罩。
            view.findDialogWindow()?.makeDecorationOnly()
            onDispose { }
        }

        val palette = rememberPalette()
        val particles = remember(tick, palette) { confettiParticles(seed = tick, palette = palette) }
        val progress = remember(tick) { Animatable(0f) }

        LaunchedEffect(tick) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(CELEBRATION_DURATION_MILLIS, easing = LinearEasing),
            )
            onFinished()
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.isEmpty()) return@Canvas
            // 从顶部中间往下撒，再被重力继续拉下来，像从顶上倾倒出来的礼花。
            val origin = Offset(size.width / 2f, size.height * CONFETTI_ORIGIN_HEIGHT)
            val span = maxOf(size.width, size.height)
            val unit = CONFETTI_BASE_SIZE.toPx()
            particles.forEach { particle ->
                val t = ((progress.value - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                val distance = particle.speed * t * span
                val x = origin.x + cos(particle.angleRadians) * distance
                val y = origin.y +
                    sin(particle.angleRadians) * distance +
                    CONFETTI_GRAVITY * t * t * span
                drawParticle(
                    particle = particle,
                    center = Offset(x, y),
                    unit = unit,
                    rotation = particle.spin * t * CONFETTI_SPIN_DEGREES,
                    alpha = particle.alphaAt(t),
                )
            }
        }
    }
}

/** 按当前主题取一组礼花颜色；主题色换掉时礼花也跟着换。 */
@Composable
private fun rememberPalette(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.error)
    }
}

/**
 * 沿着视图树往上找承载这个 Dialog 的 window：Compose 的 `Dialog` 会在内容外面套一层
 * 实现了 [DialogWindowProvider] 的容器，从内容里的任何一个 view 往上走都能碰到它。
 */
private tailrec fun View.findDialogWindow(): Window? = when (val node = parent) {
    null -> null
    is DialogWindowProvider -> node.window
    is View -> node.findDialogWindow()
    else -> null
}

/** 让窗口不接收触摸 / 不抢焦点、不画遮罩，只当一层纯展示的浮层。 */
private fun Window.makeDecorationOnly() {
    addFlags(
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    )
    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    setDimAmount(0f)
    setLayout(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
    )
}

private fun DrawScope.drawParticle(
    particle: ConfettiParticle,
    center: Offset,
    unit: Float,
    rotation: Float,
    alpha: Float,
) {
    val color = particle.color.copy(alpha = particle.color.alpha * alpha)
    val side = particle.sizeFraction * unit
    rotate(degrees = rotation, pivot = center) {
        if (particle.round) {
            drawCircle(color = color, radius = side / 2f, center = center)
        } else {
            drawRect(
                color = color,
                topLeft = Offset(center.x - side / 2f, center.y - side * CONFETTI_RIBBON_RATIO / 2f),
                size = Size(side, side * CONFETTI_RIBBON_RATIO),
            )
        }
    }
}

/** 一团礼花碎片：往哪个方向飞、飞多快、多大、转多快、什么颜色。 */
private data class ConfettiParticle(
    val angleRadians: Float,
    /** 初速度，单位是「屏幕长边」的倍数 / 全程时长。 */
    val speed: Float,
    /** 碎片尺寸，单位是 [CONFETTI_BASE_SIZE]。 */
    val sizeFraction: Float,
    /** 全程转过的角度倍数。 */
    val spin: Float,
    /** 出场延迟（占全程的比例），避免所有碎片同一帧一起出现。 */
    val delay: Float,
    val color: Color,
    val round: Boolean,
) {
    /** 前 8% 淡入、后 25% 淡出，中间保持不透明。 */
    fun alphaAt(t: Float): Float = when {
        t < CONFETTI_FADE_IN -> t / CONFETTI_FADE_IN
        t > CONFETTI_FADE_OUT_START -> ((1f - t) / (1f - CONFETTI_FADE_OUT_START)).coerceIn(0f, 1f)
        else -> 1f
    }
}

private fun confettiParticles(seed: Int, palette: List<Color>): List<ConfettiParticle> {
    val random = Random(seed)
    return List(CONFETTI_COUNT) {
        ConfettiParticle(
            // 从顶部落下：角度只取朝下的那半圈，避免碎片直接飞出屏幕外。
            angleRadians = CONFETTI_MIN_ANGLE +
                random.nextFloat() * (CONFETTI_MAX_ANGLE - CONFETTI_MIN_ANGLE),
            speed = CONFETTI_MIN_SPEED + random.nextFloat() * (CONFETTI_MAX_SPEED - CONFETTI_MIN_SPEED),
            sizeFraction = 0.6f + random.nextFloat() * 0.8f,
            spin = if (random.nextBoolean()) 1f + random.nextFloat() else -(1f + random.nextFloat()),
            delay = random.nextFloat() * CONFETTI_MAX_DELAY,
            color = palette[random.nextInt(palette.size)],
            round = random.nextFloat() < CONFETTI_ROUND_RATIO,
        )
    }
}

/** 礼花碎片数量。 */
private const val CONFETTI_COUNT = 56

/** 礼花碎片基准尺寸，实际大小在它的 0.6～1.4 倍之间。 */
private val CONFETTI_BASE_SIZE = 11.dp

/** 长条碎片的宽高比。 */
private const val CONFETTI_RIBBON_RATIO = 0.45f

/** 圆形碎片占的比例。 */
private const val CONFETTI_ROUND_RATIO = 0.45f

/** 爆炸原点的纵向位置（占屏幕高度）：0 就是贴着顶部，礼花从顶上往下撒。 */
private const val CONFETTI_ORIGIN_HEIGHT = 0f

/** 初速度范围（屏幕长边的倍数）。 */
private const val CONFETTI_MIN_SPEED = 0.22f
private const val CONFETTI_MAX_SPEED = 0.72f

/** 重力系数：乘上屏幕长边与 t² 后加到纵坐标上。 */
private const val CONFETTI_GRAVITY = 0.45f

/** 全程转过的角度（度）。 */
private const val CONFETTI_SPIN_DEGREES = 540f

/** 出场延迟上限（占全程的比例）。 */
private const val CONFETTI_MAX_DELAY = 0.18f

/** 淡入段与淡出起点的位置（占全程的比例）。 */
private const val CONFETTI_FADE_IN = 0.08f
private const val CONFETTI_FADE_OUT_START = 0.72f

/** 圆周率，用来把方向范围写成「π 的倍数」。 */
private const val PI_F = 3.1415927f

/**
 * 碎片初速度的方向范围（弧度，屏幕坐标系里 y 向下为正）：
 * 只取朝下的这半圈，让礼花一路往下飘、飘满整屏。
 */
private const val CONFETTI_MIN_ANGLE = 0.1f * PI_F
private const val CONFETTI_MAX_ANGLE = 0.9f * PI_F

/** 礼花挂载前的等待：给同时弹出的 AlertDialog 留出建窗口的时间。 */
private const val CELEBRATION_DELAY_MILLIS = 220L

/** 礼花全程时长（毫秒）。 */
private const val CELEBRATION_DURATION_MILLIS = 1_400
