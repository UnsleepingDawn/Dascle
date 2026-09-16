package com.fitplan.domain.repository

import com.fitplan.domain.model.BodyMetric
import kotlinx.datetime.LocalDate

interface BodyMetricRepository {

    suspend fun getAll(): List<BodyMetric>

    suspend fun getLatest(): BodyMetric?

    suspend fun getByDate(date: LocalDate): BodyMetric?

    /** 区间内的记录（含首尾两天），按日期从早到晚。 */
    suspend fun getBetween(start: LocalDate, end: LocalDate): List<BodyMetric>

    /** 最近一次有体重 / 体脂的记录；只看那一项，不受区间限制。 */
    suspend fun getLatestWeight(): BodyMetric?

    suspend fun getLatestBodyFat(): BodyMetric?

    /** 记录某天的体重：当天已有记录就覆盖，另一项保持不动。 */
    suspend fun recordWeight(date: LocalDate, weight: Double)

    /** 记录某天的体脂率：当天已有记录就覆盖，另一项保持不动。 */
    suspend fun recordBodyFat(date: LocalDate, bodyFat: Double)

    suspend fun insert(date: LocalDate, weight: Double?, bodyFat: Double?): Long

    suspend fun update(id: Long, weight: Double?, bodyFat: Double?)

    suspend fun deleteById(id: Long)
}
