package com.fitplan.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** 某个肌群在统计区间内记下的组数。 */
data class MuscleGroupSets(
    val muscleGroup: MuscleGroup,
    val sets: Double,
)

/** 某动作某一天的最好一组重量。 */
data class ProgressPoint(
    val date: LocalDate,
    val weight: Double,
)

/** 单个动作的重量进步曲线。 */
data class ExerciseProgress(
    val exerciseId: Long,
    val exerciseName: String,
    val points: List<ProgressPoint>,
)

/** 统计页历史列表的一项，[completedSets] 来自该次训练的已完成组。 */
data class WorkoutHistoryItem(
    val sessionId: Long,
    val name: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val completedSets: Int,
)

/** 统计页一次取数的全部结果。 */
data class WorkoutStats(
    val trainingDays: Int,
    val totalSessions: Int,
    val totalSets: Int,
    /** 近 4 周平均每周练了几天，与统计页的 7 / 30 天区间无关。 */
    val weeklyTrainingDays: Double,
    val muscleGroupSets: List<MuscleGroupSets>,
    val exerciseProgress: List<ExerciseProgress>,
    val history: List<WorkoutHistoryItem>,
) {
    val isEmpty: Boolean get() = totalSets == 0 && history.isEmpty()
}
