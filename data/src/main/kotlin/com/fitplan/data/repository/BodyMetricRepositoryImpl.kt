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
}
