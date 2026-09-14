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

/**
 * 还没落库的一组记录：补记训练时按计划目标铺出来，随整次训练一起写入，
 * 所以没有 [WorkoutSet.id] 与 [WorkoutSet.sessionId]。
 */
data class WorkoutSetDraft(
    val exerciseId: Long,
    val setIndex: Int,
    val weight: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val completed: Boolean = true,
)
