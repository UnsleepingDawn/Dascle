package com.fitplan.domain.model

import kotlinx.datetime.LocalDate

data class BodyMetric(
    val id: Long,
    val date: LocalDate,
    val weight: Double?,
    val bodyFat: Double?,
)
