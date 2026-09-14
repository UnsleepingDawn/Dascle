package com.fitplan.domain.repository

import com.fitplan.domain.model.ScheduleEntry
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

interface ScheduleRepository {

    suspend fun getAll(): List<ScheduleEntry>

    suspend fun getEnabledForDayOfWeek(dayOfWeek: DayOfWeek): List<ScheduleEntry>

    suspend fun getEnabledForDate(date: LocalDate): List<ScheduleEntry>

    suspend fun insertWeekly(routineId: Long, dayOfWeek: DayOfWeek): Long

    suspend fun insertOnce(routineId: Long, date: LocalDate): Long

    suspend fun setEnabled(id: Long, enabled: Boolean)

    suspend fun deleteById(id: Long)

    suspend fun deleteByRoutineId(routineId: Long)

    suspend fun getRestDaysBetween(start: LocalDate, endExclusive: LocalDate): List<LocalDate>

    suspend fun deleteRestDay(date: LocalDate)

    /**
     * 把编排批量铺到日历上：先清掉 `[start, endExclusive)` 内已有的「仅此日」排期与休息日，
     * 再按 [routineDates] / [restDates] 把这些天写成训练日或休息日。
     *
     * 整批放在一个事务里——中途出错或被打断时回滚，不会留下铺了一半的日历。
     * 每周循环排期（`day_of_week`）不受影响。
     */
    suspend fun applyComposePlan(
        start: LocalDate,
        endExclusive: LocalDate,
        routineDates: Map<LocalDate, Long>,
        restDates: Set<LocalDate>,
    )
}
