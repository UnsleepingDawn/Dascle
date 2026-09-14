package com.fitplan.domain.model

import kotlinx.datetime.LocalDate

/** 把 [routine] 排到某个具体日期（[specificDate]）上的一次训练。 */
data class ScheduleEntry(
    val id: Long,
    val routineId: Long,
    val routineName: String,
    val specificDate: LocalDate?,
    val enabled: Boolean,
)
