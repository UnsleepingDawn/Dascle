package com.fitplan.domain.interactor

import com.fitplan.domain.repository.ScheduleRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDate

/**
 * [after] 之后最近的一个休息日；之后不再休息则为 null。
 *
 * 供「今日休息」判断能不能顺延到下一个休息日——没有可占用的休息日时那个选项只能置灰。
 */
@Inject
class GetNextRestDay(
    private val scheduleRepository: ScheduleRepository,
) {

    suspend operator fun invoke(after: LocalDate): LocalDate? =
        scheduleRepository.getNextRestDay(after)
}
