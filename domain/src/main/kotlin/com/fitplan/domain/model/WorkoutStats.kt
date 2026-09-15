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
    /** 所选区间的第一天；「全部」时是最早一次训练的那天。 */
    val rangeStartDate: LocalDate,
    val trainingDays: Int,
    val totalSessions: Int,
    val totalSets: Int,
    /** 所选区间内的平均每周训练天数，区间越短越接近实际的练几天。 */
    val weeklyTrainingDays: Double,
    val muscleGroupSets: List<MuscleGroupSets>,
    val exerciseProgress: List<ExerciseProgress>,
    val history: List<WorkoutHistoryItem>,
) {
    val isEmpty: Boolean get() = totalSets == 0 && history.isEmpty()
}
