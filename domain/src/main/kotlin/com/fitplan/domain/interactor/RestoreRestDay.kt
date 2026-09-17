package com.fitplan.domain.interactor

import com.fitplan.domain.repository.ScheduleRepository
import com.fitplan.domain.repository.WorkoutRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/**
 * 放弃一次训练后，把 [date] 还原成休息日——这一天「什么都没剩下」时才补标记：
 * 既没有排期，也没有别的训练。
 *
 * 两个前置条件都不能省：
 * - 还有排期说明这一天本来就安排了训练（练到一半放弃，计划还在），补上休息标记会和排期打架；
 * - 还有别的训练（比如今天已经练完的那次）说明这一天确实练过，不该再标成休息。
 *
 * 只补标记、不动排期（[ScheduleRepository.addRestDay]）：中途给今天排的计划不能被抹掉，
 * 那正是 `setRestDay` 会干的事。
 */
@Inject
class RestoreRestDay(
    private val scheduleRepository: ScheduleRepository,
    private val workoutRepository: WorkoutRepository,
) {

    suspend operator fun invoke(date: LocalDate) {
        if (scheduleRepository.getEnabledForDate(date).isNotEmpty()) return

        val zone = TimeZone.currentSystemDefault()
        val sessions = workoutRepository.getSessionsBetween(
            start = date.atStartOfDayIn(zone),
            end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
        )
        if (sessions.isNotEmpty()) return

        scheduleRepository.addRestDay(date)
    }
}
