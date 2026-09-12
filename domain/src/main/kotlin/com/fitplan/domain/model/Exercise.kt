package com.fitplan.domain.model

import kotlin.time.Instant

data class Exercise(
    val id: Long,
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val description: String,
    val isCustom: Boolean,
    val createdAt: Instant,
)
