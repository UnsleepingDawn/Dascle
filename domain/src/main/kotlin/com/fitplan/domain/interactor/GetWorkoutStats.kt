package com.fitplan.domain.interactor

import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseProgress
import com.fitplan.domain.model.FrequencyPoint
import com.fitplan.domain.model.MuscleGroupVolume
import com.fitplan.domain.model.ProgressPoint
import com.fitplan.domain.model.VolumePoint
import com.fitplan.domain.model.WorkoutHistoryItem
import com.fitplan.domain.model.WorkoutSet
import com.fitplan.domain.model.WorkoutStats
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.WorkoutRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 统计页的取数：把最近 [days] 天内的已完成组按天、肌群、动作分别聚合，
 * 得到容量趋势、训练频率、肌群容量分布、动作重量进步四组数据，再附上最近若干次训练的历史。
 */
@Inject
class GetWorkoutStats(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) {

    suspend operator fun invoke(days: Int): WorkoutStats {
        val zone = TimeZone.currentSystemDefault()
        val end = Clock.System.now()
        val endDate = end.toLocalDateTime(zone).date
        val startDate = endDate.minus(days - 1, DateTimeUnit.DAY)
        val start = startDate.atStartOfDayIn(zone)

        val sessions = workoutRepository.getFinishedSessionsBetween(start = start, end = end)
        val sessionDates = sessions.associate { it.id to it.startedAt.toLocalDateTime(zone).date }
        val datedSets = workoutRepository.getCompletedSetsBetween(start = start, end = end)
            .mapNotNull { set -> sessionDates[set.sessionId]?.let { date -> DatedSet(date, set) } }
        val exercises = exerciseRepository.getAll().associateBy { it.id }

        return WorkoutStats(
            volumeTrend = volumeTrend(startDate, days, datedSets),
            frequency = frequency(startDate, days, datedSets, sessionDates.values),
            muscleGroupVolumes = muscleGroupVolumes(datedSets, exercises),
            exerciseProgress = exerciseProgress(datedSets, exercises),
            history = workoutRepository.getFinishedSessionsWithSummary(),
        )
    }

    /** 每天一个柱子；没练的日子补 0，柱子才不会跳位。 */
    private fun volumeTrend(
        startDate: LocalDate,
        days: Int,
        datedSets: List<DatedSet>,
    ): List<VolumePoint> {
        val volumeByDate = datedSets
            .groupBy { it.date }
            .mapValues { (_, sets) -> sets.sumOf { it.set.volume } }
        return datesOf(startDate, days).map { date ->
            VolumePoint(date = date, volume = volumeByDate[date] ?: 0.0)
        }
    }

    private fun frequency(
        startDate: LocalDate,
        days: Int,
        datedSets: List<DatedSet>,
        sessionDates: Collection<LocalDate>,
    ): List<FrequencyPoint> {
        val setsByDate = datedSets.groupingBy { it.date }.eachCount()
        val sessionsByDate = sessionDates.groupingBy { it }.eachCount()
        return datesOf(startDate, days).map { date ->
            FrequencyPoint(
                date = date,
                sessions = sessionsByDate[date] ?: 0,
                sets = setsByDate[date] ?: 0,
            )
        }
    }

    private fun muscleGroupVolumes(
        datedSets: List<DatedSet>,
        exercises: Map<Long, Exercise>,
    ): List<MuscleGroupVolume> = datedSets
        .mapNotNull { dated ->
            exercises[dated.set.exerciseId]?.let { it.muscleGroup to dated.set.volume }
        }
        .groupBy({ it.first }, { it.second })
        .map { (muscleGroup, volumes) -> MuscleGroupVolume(muscleGroup, volumes.sum()) }
        .filter { it.volume > 0 }
        .sortedByDescending { it.volume }

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

    private fun datesOf(startDate: LocalDate, days: Int): List<LocalDate> =
        List(days) { startDate.plus(it, DateTimeUnit.DAY) }

    private data class DatedSet(val date: LocalDate, val set: WorkoutSet)

    private companion object {
        /** 重量进步曲线最多给几个动作，太多了选择器放不下。 */
        const val MAX_PROGRESS_EXERCISES = 6
    }
}
