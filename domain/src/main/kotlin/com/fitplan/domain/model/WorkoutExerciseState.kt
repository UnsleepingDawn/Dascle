package com.fitplan.domain.model

/**
 * 一次训练里某个动作的临时状态，让「跳过动作」「减掉 / 多加的组」「动作组里挑中了谁」
 * 在退出记录页甚至杀进程后仍然保留。
 *
 * 只在用户真正动过这些状态时才会有对应行；没有行表示按计划目标默认展示。
 */
data class WorkoutExerciseState(
    val sessionId: Long,
    val exerciseId: Long,
    val skipped: Boolean = false,
    /** 记录页里该动作实际展示的组行数；null 表示用户没动过增删组，按计划目标组数铺行。 */
    val setCount: Int? = null,
    /** 动作组里是否挑中了这个动作（只对组内动作有意义）。 */
    val picked: Boolean = false,
)

/**
 * 动作组里到底挑中了哪些动作：有状态行的动作以 `picked` 为准（取消挑选也能记住），
 * 没有状态行的是升级到 schema 12 之前的老训练，退回「本次已有 `workout_set` 记录」的旧判定。
 *
 * [recordedIds] 是这次训练里出现过 `workout_set` 记录的动作 id。
 * 记录页与今日页共用这套口径，免得两处实现漂移。
 */
fun pickedExerciseIds(
    recordedIds: Collection<Long>,
    states: List<WorkoutExerciseState>,
): Set<Long> = recordedIds.toSet() - states.map { it.exerciseId } +
    states.filter { it.picked }.map { it.exerciseId }
