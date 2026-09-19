package com.fitplan.ui.exercise

import com.fitplan.domain.model.Exercise

/** 加入计划 / 动作组时的目标值：次数取动作库默认次数，重量与时长按动作类型预填。 */
internal data class RoutineTargets(
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val weight: Double?,
    val seconds: Int?,
)

/** 新排进计划的动作默认 3 组、休息 90 秒。 */
internal fun Exercise.defaultTargets(): RoutineTargets = RoutineTargets(
    sets = DEFAULT_TARGET_SETS,
    reps = repsOrDefault,
    restSeconds = DEFAULT_REST_SECONDS,
    // 纯自重动作不预填重量；计时动作用动作库的默认时长。
    weight = if (showsWeight) defaultWeight else null,
    seconds = if (isTimed) defaultDurationSeconds else null,
)

internal const val DEFAULT_TARGET_SETS = 3
internal const val DEFAULT_REST_SECONDS = 90
