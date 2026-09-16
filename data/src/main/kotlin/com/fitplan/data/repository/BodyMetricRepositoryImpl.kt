package com.fitplan.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.fitplan.data.Database
import com.fitplan.data.mapper.toDbValue
import com.fitplan.data.mapper.toDomain
import com.fitplan.domain.model.BodyMetric
import com.fitplan.domain.repository.BodyMetricRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.LocalDate

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class BodyMetricRepositoryImpl(
    private val database: Database,
) : BodyMetricRepository {

    private val queries get() = database.bodyMetricQueries
    private val utilQueries get() = database.utilQueries

    override suspend fun getAll(): List<BodyMetric> = queries.selectAll().awaitAsList().map { it.toDomain() }

    override suspend fun getLatest(): BodyMetric? = queries.selectLatest().awaitAsOneOrNull()?.toDomain()

    override suspend fun getByDate(date: LocalDate): BodyMetric? =
        queries.selectByDate(date.toDbValue()).awaitAsOneOrNull()?.toDomain()

    override suspend fun getBetween(start: LocalDate, end: LocalDate): List<BodyMetric> =
        queries.selectBetween(start.toDbValue(), end.toDbValue()).awaitAsList().map { it.toDomain() }

    override suspend fun getLatestWeight(): BodyMetric? =
        queries.selectLatestWeight().awaitAsOneOrNull()?.toDomain()

    override suspend fun getLatestBodyFat(): BodyMetric? =
        queries.selectLatestBodyFat().awaitAsOneOrNull()?.toDomain()

    override suspend fun recordWeight(date: LocalDate, weight: Double) = record(date, weight = weight)

    override suspend fun recordBodyFat(date: LocalDate, bodyFat: Double) = record(date, bodyFat = bodyFat)

    override suspend fun insert(date: LocalDate, weight: Double?, bodyFat: Double?): Long =
        database.transactionWithResult {
            queries.insert(
                date = date.toDbValue(),
                weight = weight,
                body_fat = bodyFat,
            )
            utilQueries.lastInsertRowId().awaitAsOne()
        }

    override suspend fun update(id: Long, weight: Double?, bodyFat: Double?) {
        queries.update(weight = weight, body_fat = bodyFat, id = id)
    }

    override suspend fun deleteById(id: Long) {
        queries.deleteById(id)
    }

    /**
     * 一天一行：当天已经有记录就只改这次填的那一项，另一项沿用原来的值；没有记录就新建一条。
     *
     * [weight] / [bodyFat] 只传其中一个，另一个为 null 表示「这次不填、保持不动」。
     */
    private suspend fun record(date: LocalDate, weight: Double? = null, bodyFat: Double? = null) {
        val dbDate = date.toDbValue()
        val existing = queries.selectByDate(dbDate).awaitAsOneOrNull()
        if (existing == null) {
            queries.insert(date = dbDate, weight = weight, body_fat = bodyFat)
        } else {
            queries.update(
                weight = weight ?: existing.weight,
                body_fat = bodyFat ?: existing.body_fat,
                id = existing.id,
            )
        }
    }
}
