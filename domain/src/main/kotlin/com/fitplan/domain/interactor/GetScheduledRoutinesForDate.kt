package com.fitplan.domain.interactor

import com.fitplan.domain.model.Routine
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.ScheduleRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDate

/** 某一天要练的计划，连同它已经编排好的动作。 */
data class ScheduledRoutine(
    val routine: Routine,
    val exercises: List<RoutineExercise>,
)

/**
 * 汇总某一天的训练安排：取「指定日期」（`specific_date`）排期，按计划 id 去重，
 * 并带上每个计划的动作编排，供今日页直接渲染。
 */
@Inject
class GetScheduledRoutinesForDate(
    private val scheduleRepository: ScheduleRepository,
    private val routineRepository: RoutineRepository,
) {

    suspend operator fun invoke(date: LocalDate): List<ScheduledRoutine> {
        val entries = scheduleRepository.getEnabledForDate(date)

        return entries
            .map { it.routineId }
            .distinct()
            .mapNotNull { routineId ->
                val routine = routineRepository.getById(routineId) ?: return@mapNotNull null
                ScheduledRoutine(
                    routine = routine,
                    exercises = routineRepository.getExercises(routineId),
                )
            }
    }
}
