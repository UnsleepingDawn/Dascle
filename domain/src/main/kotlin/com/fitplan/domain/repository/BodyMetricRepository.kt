package com.fitplan.domain.repository

import com.fitplan.domain.model.BodyMetric
import kotlinx.datetime.LocalDate

interface BodyMetricRepository {

    suspend fun getAll(): List<BodyMetric>

    suspend fun getLatest(): BodyMetric?

    suspend fun getByDate(date: LocalDate): BodyMetric?

    suspend fun insert(date: LocalDate, weight: Double?, bodyFat: Double?): Long

    suspend fun update(id: Long, weight: Double?, bodyFat: Double?)

    suspend fun deleteById(id: Long)
}
