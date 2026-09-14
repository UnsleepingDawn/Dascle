package com.fitplan.domain.interactor

import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseProgress
import com.fitplan.domain.model.MuscleGroupSets
import com.fitplan.domain.model.ProgressPoint
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
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 统计页的取数：把区间内的已完成组按肌群、动作分别聚合，得到肌群组数分布与动作重量进步，
 * 再附上训练天数、训练次数、完成组数、近 4 周的平均每周训练天数，以及最近若干次训练的历史。
 *
 * 「平均每周训练天数」固定看近 [FOUR_WEEKS_DAYS] 天，不受 7 / 30 天区间影响，
 * 所以区间更短时也要往前多取一段数据。
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
        val rangeStartDate = endDate.minus(days - 1, DateTimeUnit.DAY)
        val fetchStart = minOf(rangeStartDate, fourWeeksStart(endDate)).atStartOfDayIn(zone)

        val sessions = workoutRepository.getFinishedSessionsBetween(start = fetchStart, end = end)
        val sessionDates = sessions.associate { it.id to it.startedAt.toLocalDateTime(zone).date }
        val datedSets = workoutRepository.getCompletedSetsBetween(start = fetchStart, end = end)
            .mapNotNull { set -> sessionDates[set.sessionId]?.let { date -> DatedSet(date, set) } }
        val exercises = exerciseRepository.getAll().associateBy { it.id }
        val rangeSets = datedSets.filter { it.date >= rangeStartDate }

        return WorkoutStats(
            trainingDays = rangeSets.distinctBy { it.date }.size,
            totalSessions = sessionDates.count { (_, date) -> date >= rangeStartDate },
            totalSets = rangeSets.size,
            weeklyTrainingDays = weeklyTrainingDays(datedSets, endDate),
            muscleGroupSets = muscleGroupSets(rangeSets, exercises),
            exerciseProgress = exerciseProgress(rangeSets, exercises),
            history = workoutRepository.getFinishedSessionsWithSummary(),
        )
    }

    /** 近 4 周平均每周练了几天；窗口正好 [WEEKS_IN_FOUR_WEEKS] 周，直接除以它。 */
    private fun weeklyTrainingDays(datedSets: List<DatedSet>, endDate: LocalDate): Double {
        val start = fourWeeksStart(endDate)
        val trainingDays = datedSets.filter { it.date >= start }.distinctBy { it.date }.size
        return trainingDays / WEEKS_IN_FOUR_WEEKS
    }

    private fun fourWeeksStart(endDate: LocalDate): LocalDate =
        endDate.minus(FOUR_WEEKS_DAYS - 1, DateTimeUnit.DAY)

    /**
     * 一个动作的组数按部位分摊：主部位记全额，每个次部位记一半，
     * 因此各部位组数之和会略高于实际完成组数，属于预期。
     */
    private fun muscleGroupSets(
        datedSets: List<DatedSet>,
        exercises: Map<Long, Exercise>,
    ): List<MuscleGroupSets> = datedSets
        .flatMap { dated ->
            val exercise = exercises[dated.set.exerciseId] ?: return@flatMap emptyList()
            buildList {
                add(exercise.muscleGroup to 1.0)
                exercise.secondaryMuscleGroups.forEach { add(it to SECONDARY_MUSCLE_WEIGHT) }
            }
        }
        .groupBy({ it.first }, { it.second })
        .map { (muscleGroup, sets) -> MuscleGroupSets(muscleGroup, sets.sum()) }
        .sortedByDescending { it.sets }

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

        /** 「平均每周训练天数」的统计窗口：近 4 周。 */
        const val FOUR_WEEKS_DAYS = 28

        const val WEEKS_IN_FOUR_WEEKS = 4.0
    }
}
