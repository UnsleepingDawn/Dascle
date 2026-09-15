package com.fitplan.domain.interactor

import com.fitplan.domain.repository.ScheduleRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** [date] 是不是编排出来的休息日（`rest_day` 表里有这一天）。 */
@Inject
class IsRestDay(
    private val scheduleRepository: ScheduleRepository,
) {

    suspend operator fun invoke(date: LocalDate): Boolean =
        scheduleRepository.getRestDaysBetween(date, date.plus(1, DateTimeUnit.DAY)).isNotEmpty()
}
