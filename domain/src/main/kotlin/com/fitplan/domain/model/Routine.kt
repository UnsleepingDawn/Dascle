package com.fitplan.domain.model

import kotlin.time.Instant

data class Routine(
    val id: Long,
    val name: String,
    val createdAt: Instant,
    /** 日历休息日开出来的「临时计划」：只在它排到的那一天用，不进常驻计划列表。 */
    val isTemp: Boolean = false,
)
