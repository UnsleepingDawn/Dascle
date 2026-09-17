package com.fitplan.domain.interactor

import com.fitplan.domain.repository.ScheduleRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDate

/**
 * 把 [date] 的休息日标记撤掉：这一天开始训练了，就不再是休息日。
 *
 * 与「给休息日添加计划会撤掉休息标记」是同一套语义——一天不是训练日就是休息日。
 */
@Inject
class ClearRestDay(
    private val scheduleRepository: ScheduleRepository,
) {

    suspend operator fun invoke(date: LocalDate) {
        scheduleRepository.deleteRestDay(date)
    }
}
