package com.fitplan.domain.repository

import com.fitplan.domain.model.ScheduleEntry
import kotlinx.datetime.LocalDate

interface ScheduleRepository {

    suspend fun getAll(): List<ScheduleEntry>

    suspend fun getEnabledForDate(date: LocalDate): List<ScheduleEntry>

    suspend fun insertOnce(routineId: Long, date: LocalDate): Long

    suspend fun setEnabled(id: Long, enabled: Boolean)

    suspend fun deleteById(id: Long)

    suspend fun deleteByRoutineId(routineId: Long)

    suspend fun getRestDaysBetween(start: LocalDate, endExclusive: LocalDate): List<LocalDate>

    suspend fun deleteRestDay(date: LocalDate)

    /**
     * 把编排批量铺到日历上：先清掉 `[start, endExclusive)` 内已有的排期与休息日，
     * 再按 [routineDates] / [restDates] 把这些天写成训练日或休息日。
     *
     * 整批放在一个事务里——中途出错或被打断时回滚，不会留下铺了一半的日历。
     */
    suspend fun applyComposePlan(
        start: LocalDate,
        endExclusive: LocalDate,
        routineDates: Map<LocalDate, Long>,
        restDates: Set<LocalDate>,
    )

    /**
     * 顺延漏练：`[from, today)` 里 [missedRoutines] 这些天的排期清掉后写到 `date + offset` 上
     * （`offset = today - from`），同时把「[today] 及以后」的排期与休息日整体后移 `offset` 天。
     *
     * 结果为：漏掉的计划正好落在 `[today, today + offset)`，今天的原安排推到 `today + offset`。
     * 整批放在一个事务里，中途出错不会留下挪了一半的日历。
     */
    suspend fun postponeMissedPlans(
        from: LocalDate,
        today: LocalDate,
        missedRoutines: Map<LocalDate, List<Long>>,
    )

    /** 跳过漏练：清掉 [dates] 这些天的排期；休息日与今天的安排都不动。 */
    suspend fun deletePlansOn(dates: Collection<LocalDate>)
}
