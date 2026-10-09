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
 * [date] 是不是休息日：这一天既没有启用的训练排期，也没有任何训练记录。
 *
 * 休息日不再单独存储，而是由「没有安排」推导出来的——一天不是训练日就是休息日，没有第三种状态。
 * 今日页据此决定要不要整页换成休息页。
 */
@Inject
class IsRestDay(
    private val scheduleRepository: ScheduleRepository,
    private val workoutRepository: WorkoutRepository,
) {

    suspend operator fun invoke(date: LocalDate): Boolean {
        if (scheduleRepository.getEnabledForDate(date).isNotEmpty()) return false

        val zone = TimeZone.currentSystemDefault()
        val sessions = workoutRepository.getSessionsBetween(
            start = date.atStartOfDayIn(zone),
            end = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
        )
        return sessions.isEmpty()
    }
}
