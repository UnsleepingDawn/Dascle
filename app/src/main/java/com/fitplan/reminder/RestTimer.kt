package com.fitplan.reminder

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** 组间休息倒计时；[remainingSeconds] 为 0 表示刚刚结束。 */
data class RestState(
    val exerciseName: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
)

/**
 * 组间休息计时器：app 级单例，与页面生命周期脱钩，所以退出记录页、切到别的 Tab 都照常计时。
 *
 * 计时以「截止时刻」为准而不是累加 `delay`：进程被系统冻结时回到前台也能算出正确剩余。
 * 到点的提醒分两处：留在前台由本类震动；退到后台后由 [RestNotifier] 的系统倒计时通知与
 * 到点精确闹钟接管（那时本协程可能已被冻结，震动交给闹钟，避免重复震）。
 *
 * 归零后不会立刻清空 [rest]，而是保留一小段时间（[FINISH_HOLD_MILLIS]）让界面播完
 * 「休息结束」的动画，然后才自动收起。
 */
@Inject
@SingleIn(AppScope::class)
class RestTimer(
    private val context: Context,
    private val restNotifier: RestNotifier,
) {

    /** app 进程存活期间不取消，所以退出记录页也能继续走完这次休息。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var restJob: Job? = null

    private val _rest = MutableStateFlow<RestState?>(null)

    /** 非空表示正在休息或刚结束的展示窗口。 */
    val rest: StateFlow<RestState?> = _rest.asStateFlow()

    /** 每归零一次自增，界面据此做一次性动画（如闹钟摇摆）。 */
    private val _finishedTick = MutableStateFlow(0)
    val finishedTick: StateFlow<Int> = _finishedTick.asStateFlow()

    /** 开始一次组间休息；[seconds] 不为正时直接清掉计时。 */
    fun start(exerciseName: String, seconds: Int) {
        restJob?.cancel()
        if (seconds <= 0) {
            _rest.value = null
            restNotifier.cancel()
            return
        }

        val endAt = Clock.System.now() + seconds.seconds
        // 前台由本协程刷新界面；退到后台后由通知栏的系统倒计时接管。
        restNotifier.start(exerciseName, endAt)
        restJob = scope.launch {
            while (true) {
                val remaining = remainingSeconds(endAt)
                _rest.value = RestState(exerciseName, seconds, remaining)
                if (remaining <= 0) break
                delay(REST_TICK_MILLIS)
            }
            // 留在前台才由这里震动；后台已由到点闹钟负责，避免震两次。
            if (isInForeground()) vibrate()
            _finishedTick.value += 1
            // 留一会儿「休息结束」，然后自动收起。
            delay(FINISH_HOLD_MILLIS)
            _rest.value = null
        }
    }

    /** 跳过休息、结束或放弃训练：撤掉倒计时、后台通知与到点闹钟。 */
    fun cancel() {
        restJob?.cancel()
        restJob = null
        _rest.value = null
        restNotifier.cancel()
    }

    /** 按截止时刻反算剩余秒数（向上取整，避免显示比实际少一秒）。 */
    private fun remainingSeconds(endAt: Instant): Int {
        val millis = (endAt - Clock.System.now()).inWholeMilliseconds
        return if (millis <= 0) 0 else ((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
    }

    private fun isInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    @SuppressLint("MissingPermission")
    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private companion object {
        const val REST_TICK_MILLIS = 1_000L
        const val FINISH_HOLD_MILLIS = 2_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val VIBRATION_MILLIS = 400L
    }
}
