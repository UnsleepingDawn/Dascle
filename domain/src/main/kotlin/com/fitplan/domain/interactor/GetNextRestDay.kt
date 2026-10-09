package com.fitplan.domain.interactor

import com.fitplan.domain.repository.ScheduleRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * [after] 之后最近的一个休息日（第一个没有启用排期的日子）；往后每一天都被排满则为 null。
 *
 * 休息日不单独存储，只能从排期反推：把启用排期的日期收集起来，从 [after] 的次日往后找第一个空日。
 * 供「今日休息」判断能不能顺延到下一个休息日——没有可占用的休息日时那个选项只能置灰。
 */
@Inject
class GetNextRestDay(
    private val scheduleRepository: ScheduleRepository,
) {

    suspend operator fun invoke(after: LocalDate): LocalDate? {
        val scheduledDates = scheduleRepository.getAll()
            .filter { it.enabled && it.specificDate != null }
            .mapNotNull { it.specificDate }
            .toSet()

        var date = after.plus(1, DateTimeUnit.DAY)
        repeat(MAX_LOOKAHEAD_DAYS) {
            if (date !in scheduledDates) return date
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return null
    }

    private companion object {
        /** 最多往后找这么多天：连续排满时也不至于一直找下去。 */
        const val MAX_LOOKAHEAD_DAYS = 366
    }
}
