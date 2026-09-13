package com.fitplan.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** 容量趋势上的一天：当天所有已完成组的「重量 × 次数」之和。 */
data class VolumePoint(
    val date: LocalDate,
    val volume: Double,
)

/** 训练频率上的一天：当天练了几场、完成了几组。 */
data class FrequencyPoint(
    val date: LocalDate,
    val sessions: Int,
    val sets: Int,
)

/** 某个肌群在统计区间内的总容量。 */
data class MuscleGroupVolume(
    val muscleGroup: MuscleGroup,
    val volume: Double,
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

/** 统计页历史列表的一项，[completedSets] 与 [volume] 来自该次训练的已完成组。 */
data class WorkoutHistoryItem(
    val sessionId: Long,
    val name: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val completedSets: Int,
    val volume: Double,
)

/** 统计页一次取数的全部结果。 */
data class WorkoutStats(
    val volumeTrend: List<VolumePoint>,
    val frequency: List<FrequencyPoint>,
    val muscleGroupVolumes: List<MuscleGroupVolume>,
    val exerciseProgress: List<ExerciseProgress>,
    val history: List<WorkoutHistoryItem>,
) {
    val totalVolume: Double get() = volumeTrend.sumOf { it.volume }

    val totalSessions: Int get() = frequency.sumOf { it.sessions }

    val totalSets: Int get() = frequency.sumOf { it.sets }

    val isEmpty: Boolean get() = totalSets == 0 && history.isEmpty()
}
