package com.fitplan.domain.model

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * 单个动作的渐进重量提示状态，动作之间互不影响。
 *
 * [snoozeCount] 记录用户点了几次「暂时别提醒我加重量」，决定下一次推迟多久；
 * [nextRemindAt] 非空表示暂缓中，到点后重新可提醒；档位用尽后进入 [isDormant]，
 * 不再按时间提醒，只等用户主动加重量时清空这条记录。
 */
data class ExerciseProgressHint(
    val exerciseId: Long,
    val snoozeCount: Int = 0,
    val nextRemindAt: Instant? = null,
) {

    /** 暂缓档位已用尽：不再自动提醒，只能由用户主动加重量解除。 */
    val isDormant: Boolean get() = snoozeCount >= MAX_SNOOZE_COUNT && nextRemindAt == null

    /** [now] 这个时刻能不能弹提示：暂缓期内与休眠期都不弹。 */
    fun canRemindAt(now: Instant): Boolean = !isDormant && (nextRemindAt == null || now >= nextRemindAt)

    /** 再点一次「暂时别提醒」之后的状态：档位 3/5/7/9 天，用尽后 [nextRemindAt] 为空进入休眠。 */
    fun snoozed(now: Instant): ExerciseProgressHint {
        val nextCount = snoozeCount + 1
        return copy(
            snoozeCount = nextCount,
            nextRemindAt = SNOOZE_DAYS.getOrNull(nextCount - 1)?.let { now + it.days },
        )
    }

    companion object {
        /** 第 1~4 次点「暂时别提醒我加重量」分别推迟 3 / 5 / 7 / 9 天。 */
        val SNOOZE_DAYS = listOf(3, 5, 7, 9)

        /** 点满这么多次就休眠；比 [SNOOZE_DAYS] 多一次，用来表示「最后一次点击」。 */
        const val MAX_SNOOZE_COUNT = 5
    }
}
