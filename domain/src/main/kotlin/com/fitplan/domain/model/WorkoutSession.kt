package com.fitplan.domain.model

import kotlin.time.Instant

data class WorkoutSession(
    val id: Long,
    val routineId: Long?,
    val name: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val note: String,
) {
    val isFinished: Boolean get() = finishedAt != null
}
