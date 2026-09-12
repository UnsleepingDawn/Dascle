package com.fitplan.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * 把 [routine] 排到每周的某一天（[dayOfWeek]），或某个具体日期（[specificDate]）。
 * 两者互斥，同一时间只会有一个非空。
 */
data class ScheduleEntry(
    val id: Long,
    val routineId: Long,
    val routineName: String,
    val dayOfWeek: DayOfWeek?,
    val specificDate: LocalDate?,
    val enabled: Boolean,
)
