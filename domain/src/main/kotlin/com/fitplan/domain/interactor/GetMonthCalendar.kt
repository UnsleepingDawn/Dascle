package com.fitplan.domain.interactor

import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.ScheduleRepository
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

/** 日历某天里排入的一个计划，[entryId] 对应 `schedule_entry` 的一条排期。 */
data class CalendarPlan(
    val entryId: Long,
    val routineId: Long,
    val routineName: String,
    val note: String,
    val exerciseCount: Int,
    val muscleGroups: List<MuscleGroup>,
    /** true 表示「每周循环」排期，false 表示「仅此日」。 */
    val isWeekly: Boolean,
)

/** 日历某天实际完成的一次训练。 */
data class CalendarSession(
    val sessionId: Long,
    val name: String,
    val completedSets: Int,
)

/**
 * 训练日历上的一天。
 *
 * [muscleGroups] 是格子里显示的肌群：过去的日子取当天实际练到的肌群，今天及以后取排期计划里的肌群；
 * [isTrainingDay] 同理，过去看是否真的练了，今天及以后看有没有排期。
 */
data class CalendarDay(
    val date: LocalDate,
    val isToday: Boolean,
    val isPast: Boolean,
    val planned: List<CalendarPlan>,
    val actualSessions: List<CalendarSession>,
    val muscleGroups: List<MuscleGroup>,
) {
    val isTrainingDay: Boolean
        get() = if (isPast) actualSessions.isNotEmpty() else planned.isNotEmpty()
}

/**
 * 取 [anyDayInMonth] 所在月的训练日历：
 *
 * - 今天及以后：按「每周循环 + 指定日期」两条排期推算每天要练的计划与肌群；
 * - 今天以前：用已结束训练的实际组数，算出当天练到的肌群。
 *
 * 无论哪天，[CalendarDay.planned] 都来自排期，供点开某天时查看「当天计划」。
 */
@Inject
class GetMonthCalendar(
    private val scheduleRepository: ScheduleRepository,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) {

    suspend operator fun invoke(anyDayInMonth: LocalDate): List<CalendarDay> {
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(zone).date
        val firstDay = anyDayInMonth.minus(anyDayInMonth.day - 1, DateTimeUnit.DAY)
        val nextMonth = firstDay.plus(1, DateTimeUnit.MONTH)
        val days = generateSequence(firstDay) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it < nextMonth }
            .toList()

        val entries = scheduleRepository.getAll().filter { it.enabled }
        val weeklyByDay = entries.filter { it.dayOfWeek != null }.groupBy { it.dayOfWeek }
        val onceByDate = entries.filter { it.specificDate != null }.groupBy { it.specificDate }

        val routines = routineRepository.getAll().associateBy { it.id }
        val exercisesByRoutine = mutableMapOf<Long, List<RoutineExercise>>()

        val start = firstDay.atStartOfDayIn(zone)
        val end = nextMonth.atStartOfDayIn(zone)
        val sessions = workoutRepository.getFinishedSessionsBetween(start = start, end = end)
        val sessionDate = sessions.associate { it.id to it.startedAt.toLocalDateTime(zone).date }
        val sets = workoutRepository.getCompletedSetsBetween(start = start, end = end)
        val muscleByExercise = exerciseRepository.getAll().associate { it.id to it.muscleGroup }
        val sessionsByDate = sessions.groupBy { sessionDate.getValue(it.id) }
        val setsBySession = sets.groupBy { it.sessionId }

        return days.map { date ->
            val dayEntries = weeklyByDay[date.dayOfWeek].orEmpty() + onceByDate[date].orEmpty()
            val planned = dayEntries
                .distinctBy { it.routineId }
                .mapNotNull { entry ->
                    val routine = routines[entry.routineId] ?: return@mapNotNull null
                    val exercises = exercisesByRoutine.getOrPut(routine.id) {
                        routineRepository.getExercises(routine.id)
                    }
                    CalendarPlan(
                        entryId = entry.id,
                        routineId = routine.id,
                        routineName = routine.name,
                        note = routine.note,
                        exerciseCount = exercises.size,
                        muscleGroups = exercises.map { it.muscleGroup }.distinct(),
                        isWeekly = entry.dayOfWeek != null,
                    )
                }

            val daySessions = sessionsByDate[date].orEmpty()
            val actualSessions = daySessions.map { session ->
                CalendarSession(
                    sessionId = session.id,
                    name = session.name.ifBlank { session.routineId?.let { routines[it]?.name }.orEmpty() },
                    completedSets = setsBySession[session.id].orEmpty().size,
                )
            }
            val actualMuscleGroups = daySessions
                .flatMap { setsBySession[it.id].orEmpty() }
                .mapNotNull { muscleByExercise[it.exerciseId] }
                .distinct()

            val isPast = date < today
            CalendarDay(
                date = date,
                isToday = date == today,
                isPast = isPast,
                planned = planned,
                actualSessions = actualSessions,
                muscleGroups = if (isPast) actualMuscleGroups else planned.flatMap { it.muscleGroups }.distinct(),
            )
        }
    }
}
