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
)

/** 日历某天实际完成的一次训练；[completedSets] 是这次训练记录的已完成组数。 */
data class CalendarSession(
    val sessionId: Long,
    val routineId: Long?,
    val name: String,
    val completedSets: Int,
)

/**
 * 训练日历上的一天。
 *
 * [muscleGroups] 是格子里显示的肌群：过去的日子只看实际练到的肌群，今天及以后优先取排期计划里的肌群，
 * 今天已经开练却没有排期时（休息日的「临时方案」）退回实际练到的肌群；
 * [isTrainingDay] 表示这一天算不算训练日：过去看有没有训练记录，今天及以后看有没有排期或者已经开练。
 * [isRestDay] 表示这一天被编排成了休息日。
 *
 * 过去的日子里遗留的排期（当时排了却没练）不参与上面两项——过去没法再规划，明细里也不显示
 * 「当天计划」，这种日子在格子里保持空白，免得出现「有肌群却没有任何训练」的格子。
 * 这些排期仍留在库里，因为它们正是漏练检查（`GetMissedTraining`）的判断依据。
 */
data class CalendarDay(
    val date: LocalDate,
    val isToday: Boolean,
    val isPast: Boolean,
    val planned: List<CalendarPlan>,
    val actualSessions: List<CalendarSession>,
    val muscleGroups: List<MuscleGroup>,
    val isRestDay: Boolean = false,
) {
    val isTrainingDay: Boolean
        get() = if (isPast) {
            actualSessions.isNotEmpty()
        } else {
            planned.isNotEmpty() || actualSessions.isNotEmpty()
        }
}

/**
 * 取 [anyDayInMonth] 所在月的训练日历：
 *
 * - 今天及以后：按当天的排期推算要练的计划与肌群；今天已经开练的也算进来；
 * - 今天以前：用已结束训练的实际组数，算出当天练到的肌群。
 *
 * [CalendarDay.planned] 无论哪天都来自排期，只有「今天及以后、且还没开练」的日子才拿它渲染
 * 「当天计划」；今天开练后（练完或中途退出都算）与过去的日子都只渲染 [CalendarDay.actualSessions]。
 * 给过去的日子加计划走 [RecordPastWorkout] 补记成实际训练，所以过去某天只会给出
 * [CalendarDay.actualSessions]。
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
        val onceByDate = entries.filter { it.specificDate != null }.groupBy { it.specificDate }
        val restDays = scheduleRepository.getRestDaysBetween(firstDay, nextMonth).toSet()

        val routines = routineRepository.getAll().associateBy { it.id }
        val exercisesByRoutine = mutableMapOf<Long, List<RoutineExercise>>()

        val start = firstDay.atStartOfDayIn(zone)
        val end = nextMonth.atStartOfDayIn(zone)
        // 今天还没结束的那次训练（休息日的「临时方案」练到一半退出）也要算成今天练过，
        // 否则今天会顶着一张「休息」。隔天遗留的未完成训练仍不显示，与桌面组件口径一致。
        val ongoingToday = workoutRepository
            .getSessionsBetween(
                start = today.atStartOfDayIn(zone),
                end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
            )
            .filterNot { it.isFinished }
        val sessions = workoutRepository.getFinishedSessionsBetween(start = start, end = end) + ongoingToday
        val sessionDate = sessions.associate { it.id to it.startedAt.toLocalDateTime(zone).date }
        // 未结束的训练拿不到「区间内已完成组」那条查询（它按 finished_at 过滤），按 session 单独取。
        val sets = workoutRepository.getCompletedSetsBetween(start = start, end = end) +
            ongoingToday.flatMap { session ->
                workoutRepository.getSets(session.id).filter { it.completed }
            }
        val muscleByExercise = exerciseRepository.getAll().associate { it.id to it.muscleGroups }
        val sessionsByDate = sessions.groupBy { sessionDate.getValue(it.id) }
        val setsBySession = sets.groupBy { it.sessionId }

        return days.map { date ->
            val dayEntries = onceByDate[date].orEmpty()
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
                        muscleGroups = exercises.flatMap { it.muscleGroups }.distinct(),
                    )
                }

            val daySessions = sessionsByDate[date].orEmpty()
            val actualSessions = daySessions.map { session ->
                CalendarSession(
                    sessionId = session.id,
                    routineId = session.routineId,
                    name = session.name.ifBlank { session.routineId?.let { routines[it]?.name }.orEmpty() },
                    completedSets = setsBySession[session.id].orEmpty().size,
                )
            }
            val actualMuscleGroups = daySessions
                .flatMap { setsBySession[it.id].orEmpty() }
                .flatMap { muscleByExercise[it.exerciseId].orEmpty() }
                .distinct()

            val isPast = date < today
            // 过去的日子只认实际练到的肌群。曾经这里会在「当天没练、却排了计划」时退回计划肌群，
            // 那是给「事后给过去补排计划」准备的显示；现在过去加计划会直接补记成实际训练，
            // 遗留排期再冒出来就会变成「有肌群、点进去却没练」的错觉。
            // 今天及以后优先显示排期要练的部位；今天已经开练却没有排期（休息日的「临时方案」），
            // 退回实际练到的部位，格子才不会空着。
            val muscleGroups = if (isPast) {
                actualMuscleGroups
            } else {
                planned.flatMap { it.muscleGroups }.distinct().ifEmpty { actualMuscleGroups }
            }
            CalendarDay(
                date = date,
                isToday = date == today,
                isPast = isPast,
                planned = planned,
                actualSessions = actualSessions,
                muscleGroups = muscleGroups,
                isRestDay = date in restDays,
            )
        }
    }
}
