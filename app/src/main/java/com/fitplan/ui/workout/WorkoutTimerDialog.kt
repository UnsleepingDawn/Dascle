package com.fitplan.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitplan.app.R
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.reminder.vibrateOnce
import kotlinx.coroutines.delay
import kotlin.time.Clock

/**
 * 计时弹窗所处的阶段：先敲 3-2-1 热身，再报一句「开始训练！」，然后才正式计时。
 *
 * 三个阶段共用同一块显示区域，所以切换时高度不会有任何变化。
 */
private enum class TimerPhase { COUNT_IN, READY, RUNNING }

/**
 * 计时类动作的计时弹窗：点组行右侧的「开始」打开。
 *
 * 弹出后先 3-2-1 倒数（每敲一个震一下），再显示「开始训练！」并马上开始计时。计时界面默认是
 * 正计时，可以就地切成倒数器；[countdownSeconds] 是倒数器的原点，也就是打开弹窗那一刻输入框里填的
 * 时长。倒数到 0 只震动一次并显示「时间到」，计时**不会**停，用户还可以接着往下练。
 *
 * 底部的两个按钮是蓝色文字按钮：左「返回」= 停表退出、这一组不记录；右「已经做完了」= 停表退出并
 * 把这一组记为完成。点遮罩与系统返回键都等同于「返回」。
 *
 * 这里只是一个前台计时显示，不写任何数据：这一组最终记多久，仍然以输入框里填的时长为准。
 */
@Composable
internal fun WorkoutTimerDialog(
    exerciseName: String,
    setLabel: String,
    countdownSeconds: Int,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf(TimerPhase.COUNT_IN) }
    var countInNumber by remember { mutableIntStateOf(COUNT_IN_FROM) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var countdownMode by remember { mutableStateOf(false) }
    // 倒数到点只震一次，所以记下是否已经提醒过。
    var timeUpNotified by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 3-2-1：每个数字停一秒，敲下去的同时震一下。
        for (number in COUNT_IN_FROM downTo 1) {
            countInNumber = number
            vibrateOnce(context, COUNT_IN_VIBRATION_MILLIS)
            delay(COUNT_IN_MILLIS)
        }
        phase = TimerPhase.READY
        delay(READY_MILLIS)

        val startedAt = Clock.System.now()
        phase = TimerPhase.RUNNING
        while (true) {
            // 按「起点 + 已过去多久」反算，而不是一秒一秒累加：界面被系统卡住时也不会跑偏。
            val elapsed = (Clock.System.now() - startedAt).inWholeMilliseconds
            elapsedMillis = elapsed
            if (!timeUpNotified && countdownSeconds > 0 && elapsed >= countdownSeconds * MILLIS_PER_SECOND) {
                timeUpNotified = true
                vibrateOnce(context)
            }
            delay(TICK_MILLIS)
        }
    }

    val remainingSeconds = countdownRemainingSeconds(countdownSeconds, elapsedMillis)

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = TIMER_DIALOG_TONAL_ELEVATION,
            modifier = Modifier.sizeIn(
                minWidth = TIMER_DIALOG_MIN_WIDTH,
                maxWidth = TIMER_DIALOG_MAX_WIDTH,
            ),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = TIMER_DIALOG_SIDE_PADDING,
                    end = TIMER_DIALOG_SIDE_PADDING,
                    top = TIMER_DIALOG_TOP_PADDING,
                    bottom = TIMER_DIALOG_BOTTOM_PADDING,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = setLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 三个阶段共用这块固定高度的显示区，切换时弹窗高度纹丝不动。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TIMER_DISPLAY_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    when (phase) {
                        TimerPhase.COUNT_IN -> TimerCountInText(countInNumber)

                        TimerPhase.READY -> Text(
                            text = stringResource(R.string.workout_timer_ready),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        TimerPhase.RUNNING -> {
                            if (countdownMode && remainingSeconds == 0) {
                                // 倒数到底就报一句「时间到」，计时本身继续走，等用户自己收尾。
                                Text(
                                    text = stringResource(R.string.workout_timer_time_up),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    text = if (countdownMode) {
                                        timerText(remainingSeconds * MILLIS_PER_SECOND)
                                    } else {
                                        timerText(elapsedMillis)
                                    },
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        // 等宽数字：位数变化时字形宽度不变，数字不会左右抖动。
                                        fontFeatureSettings = TABULAR_NUMBERS,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                TextButton(onClick = { countdownMode = !countdownMode }) {
                    Text(
                        text = stringResource(
                            if (countdownMode) {
                                R.string.workout_timer_to_stopwatch
                            } else {
                                R.string.workout_timer_to_countdown
                            },
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // TextButton 默认就是主色（蓝色）文字，不必再指定颜色。
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.action_back))
                    }
                    TextButton(onClick = onFinish) {
                        Text(text = stringResource(R.string.workout_timer_finish))
                    }
                }
            }
        }
    }
}

/** 3-2-1 里那个数字：和计时数字同一块区域、同一套字级，切换时不跳。 */
@Composable
private fun TimerCountInText(number: Int) {
    Text(
        text = number.toString(),
        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = TABULAR_NUMBERS),
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * 把「已经过去多少毫秒」格式化成计时文本：不足一小时是 `mm:ss`，超过则 `h:mm:ss`。
 *
 * 向下取整到秒——计时器显示的是「已经满了多少秒」，所以 59.9 秒显示 `00:59`。
 */
internal fun timerText(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val seconds = (totalSeconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')
    val minutes = ((totalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR).toString().padStart(2, '0')
    val hours = totalSeconds / SECONDS_PER_HOUR
    return if (hours > 0) "$hours:$minutes:$seconds" else "$minutes:$seconds"
}

/**
 * 倒数器还剩多少秒：目标秒数减去已经过去的整秒，下限为 0（数过了就一直停在 0）。
 */
internal fun countdownRemainingSeconds(targetSeconds: Int, elapsedMillis: Long): Int {
    val elapsedSeconds = (elapsedMillis / MILLIS_PER_SECOND).coerceAtLeast(0L)
    return (targetSeconds - elapsedSeconds).coerceAtLeast(0L).toInt()
}

/** 3-2-1 从几开始数。 */
private const val COUNT_IN_FROM = 3

/** 3-2-1 每个数字停留的时长（毫秒）。 */
private const val COUNT_IN_MILLIS = 1_000L

/** 「开始训练！」停留的时长（毫秒），之后立刻进入计时。 */
private const val READY_MILLIS = 800L

/** 计时刷新间隔（毫秒）。显示只到秒，取 200 足以让秒数一变就翻上去。 */
private const val TICK_MILLIS = 200L

/** 3-2-1 每敲一个数字的震动时长（毫秒）；比默认短一些，呼应「滴」的一下。 */
private const val COUNT_IN_VIBRATION_MILLIS = 120L

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = 3_600L

/** 等宽数字的字形特性标记，让计时数字不随位数变化左右抖动。 */
private const val TABULAR_NUMBERS = "tnum"

/** 计时弹窗的宽度与内边距；比 Material3 默认弹窗紧凑，也更窄，像一个专门的计时器。 */
private val TIMER_DIALOG_MIN_WIDTH = 280.dp
private val TIMER_DIALOG_MAX_WIDTH = 320.dp
private val TIMER_DIALOG_SIDE_PADDING = 24.dp
private val TIMER_DIALOG_TOP_PADDING = 20.dp
private val TIMER_DIALOG_BOTTOM_PADDING = 12.dp
private val TIMER_DIALOG_TONAL_ELEVATION = 6.dp

/** 数字 / 「开始训练！」共用显示区的高度，按 `displayLarge` 的行高留够。 */
private val TIMER_DISPLAY_HEIGHT = 88.dp
