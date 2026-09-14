package com.fitplan.domain.model

/**
 * 一组训练记录。重量/次数类动作用 [weight] + [reps]，
 * 计时类动作（平板支撑等）用 [durationSeconds]，两者二选一。
 */
data class WorkoutSet(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val setIndex: Int,
    val weight: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val completed: Boolean,
)
