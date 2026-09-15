package com.fitplan.domain.interactor

import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseProgress
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.model.MuscleGroupSets
import com.fitplan.domain.model.ProgressPoint
import com.fitplan.domain.model.StatsRange
import com.fitplan.domain.model.WorkoutSet
import com.fitplan.domain.model.WorkoutStats
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.WorkoutRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 统计页的取数：把 [range] 区间内的已完成组按肌群、动作分别聚合，得到肌群组数分布与动作重量进步，
 * 再附上区间内的训练天数、训练次数、完成组数与平均每周训练天数，以及区间内最近的几条训练历史
 * （[MAX_HISTORY_ITEMS] 条，按开始时间从新到旧）。
 *
 * 页面上所有数字与列表都由同一个区间决定：切到近 7 天就只统计这 7 天，「全部」则回溯到最早一次训练。
 */
@Inject
class GetWorkoutStats(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) {

    suspend operator fun invoke(range: StatsRange): WorkoutStats {
        val zone = TimeZone.currentSystemDefault()
        val end = Clock.System.now()
        val endDate = end.toLocalDateTime(zone).date
        val sessions = workoutRepository.getFinishedSessions()

        // 固定区间往前数 [range] 天；「全部」则退到最早一次已结束训练那天。一次没练过就用今天，统计结果为空。
        val rangeStartDate = range.days?.let { endDate.minus(it - 1, DateTimeUnit.DAY) }
            ?: sessions.minOfOrNull { it.startedAt.toLocalDateTime(zone).date }
            ?: endDate
        val fetchStart = rangeStartDate.atStartOfDayIn(zone)

        val rangeSessions = sessions.filter { it.startedAt >= fetchStart }
        val sessionDates = rangeSessions.associate { it.id to it.startedAt.toLocalDateTime(zone).date }
        val datedSets = workoutRepository.getCompletedSetsBetween(start = fetchStart, end = end)
            .mapNotNull { set -> sessionDates[set.sessionId]?.let { date -> DatedSet(date, set) } }
        val exercises = exerciseRepository.getAll().associateBy { it.id }

        return WorkoutStats(
            rangeStartDate = rangeStartDate,
            trainingDays = datedSets.distinctBy { it.date }.size,
            totalSessions = rangeSessions.size,
            totalSets = datedSets.size,
            weeklyTrainingDays = weeklyTrainingDays(datedSets, rangeStartDate, endDate),
            muscleGroupSets = muscleGroupSets(datedSets, exercises),
            exerciseProgress = exerciseProgress(datedSets, exercises),
            history = workoutRepository.getFinishedSessionsWithSummary(start = fetchStart, end = end)
                .sortedByDescending { it.startedAt }
                .take(MAX_HISTORY_ITEMS),
        )
    }

    /**
     * 区间内的平均每周训练天数：把区间的训练天数按 7 天一周折算，近 7 天就等于这个区间的训练天数。
     * 「全部」区间可能横跨几年，数值会被摊得很小，属于预期。
     */
    private fun weeklyTrainingDays(
        datedSets: List<DatedSet>,
        rangeStartDate: LocalDate,
        endDate: LocalDate,
    ): Double {
        val rangeDays = rangeStartDate.daysUntil(endDate) + 1
        val trainingDays = datedSets.distinctBy { it.date }.size
        return trainingDays * DAYS_PER_WEEK / rangeDays
    }

    /**
     * 一个动作的组数按部位分摊：主部位记全额，每个次部位记一半，
     * 因此各部位组数之和会略高于实际完成组数，属于预期。
     *
     * 横轴按 [MuscleGroup.chartOrder] 铺满全部肌群，没数据的补 0，
     * 这样不同区间的柱子位置一致，也不会出现「没练过的肌群直接消失」。
     */
    private fun muscleGroupSets(
        datedSets: List<DatedSet>,
        exercises: Map<Long, Exercise>,
    ): List<MuscleGroupSets> {
        val setsByGroup = datedSets
            .flatMap { dated ->
                val exercise = exercises[dated.set.exerciseId] ?: return@flatMap emptyList()
                buildList {
                    add(exercise.muscleGroup to 1.0)
                    exercise.secondaryMuscleGroups.forEach { add(it to SECONDARY_MUSCLE_WEIGHT) }
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, sets) -> sets.sum() }

        return MuscleGroup.chartOrder.map { group ->
            MuscleGroupSets(muscleGroup = group, sets = setsByGroup[group] ?: 0.0)
        }
    }

    /** 只挑有重量记录的动作，取数据点最多的前几个给图表做选择器。 */
    private fun exerciseProgress(
        datedSets: List<DatedSet>,
        exercises: Map<Long, Exercise>,
    ): List<ExerciseProgress> = datedSets
        .filter { it.set.weight != null }
        .groupBy { it.set.exerciseId }
        .mapNotNull { (exerciseId, sets) ->
            val exercise = exercises[exerciseId] ?: return@mapNotNull null
            val points = sets
                .groupBy { it.date }
                .map { (date, daySets) -> ProgressPoint(date, daySets.maxOf { it.set.weight ?: 0.0 }) }
                .sortedBy { it.date }
            ExerciseProgress(exerciseId = exerciseId, exerciseName = exercise.name, points = points)
        }
        .sortedByDescending { it.points.size }
        .take(MAX_PROGRESS_EXERCISES)

    private data class DatedSet(val date: LocalDate, val set: WorkoutSet)

    private companion object {
        /** 重量进步曲线最多给几个动作，太多了选择器放不下。 */
        const val MAX_PROGRESS_EXERCISES = 6

        /** 次部位分摊到的组数比例。 */
        const val SECONDARY_MUSCLE_WEIGHT = 0.5

        /** 训练历史列表最多显示几条，只保留最近的几次。 */
        const val MAX_HISTORY_ITEMS = 5

        /** 平均每周训练天数的折算：一周 7 天。 */
        const val DAYS_PER_WEEK = 7.0
    }
}
