package com.fitplan.domain.model

import kotlin.time.Instant

data class Routine(
    val id: Long,
    val name: String,
    val createdAt: Instant,
)
