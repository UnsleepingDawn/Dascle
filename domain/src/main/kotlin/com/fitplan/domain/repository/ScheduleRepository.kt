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
}
