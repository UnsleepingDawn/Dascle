package com.fitplan.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.fitplan.data.Database
import com.fitplan.data.mapper.toDbValue
import com.fitplan.data.mapper.toDomain
import com.fitplan.data.mapper.toLocalDate
import com.fitplan.domain.model.ScheduleEntry
import com.fitplan.domain.repository.ScheduleRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ScheduleRepositoryImpl(
    private val database: Database,
) : ScheduleRepository {

    private val queries get() = database.scheduleEntryQueries
    private val restQueries get() = database.restDayQueries
    private val utilQueries get() = database.utilQueries

    override suspend fun getAll(): List<ScheduleEntry> = queries.selectAll().awaitAsList().map { it.toDomain() }

    override suspend fun getEnabledForDayOfWeek(dayOfWeek: DayOfWeek): List<ScheduleEntry> =
        queries.selectEnabledByDayOfWeek(dayOfWeek.toDbValue()).awaitAsList().map { it.toDomain() }

    override suspend fun getEnabledForDate(date: LocalDate): List<ScheduleEntry> =
        queries.selectEnabledBySpecificDate(date.toDbValue()).awaitAsList().map { it.toDomain() }

    override suspend fun insertWeekly(routineId: Long, dayOfWeek: DayOfWeek): Long =
        database.transactionWithResult {
            queries.insert(
                routine_id = routineId,
                day_of_week = dayOfWeek.toDbValue(),
                specific_date = null,
                enabled = 1L,
            )
            utilQueries.lastInsertRowId().awaitAsOne()
        }

    override suspend fun insertOnce(routineId: Long, date: LocalDate): Long =
        database.transactionWithResult {
            queries.insert(
                routine_id = routineId,
                day_of_week = null,
                specific_date = date.toDbValue(),
                enabled = 1L,
            )
            utilQueries.lastInsertRowId().awaitAsOne()
        }

    override suspend fun getRestDaysBetween(start: LocalDate, endExclusive: LocalDate): List<LocalDate> =
        restQueries.selectBetween(
            date = start.toDbValue(),
            date_ = endExclusive.toDbValue(),
        ).awaitAsList().map { it.toLocalDate() }

    override suspend fun deleteRestDay(date: LocalDate) {
        restQueries.deleteByDate(date = date.toDbValue())
    }

    override suspend fun applyComposePlan(
        start: LocalDate,
        endExclusive: LocalDate,
        routineDates: Map<LocalDate, Long>,
        restDates: Set<LocalDate>,
    ) {
        database.transactionWithResult {
            queries.deleteOnceBetween(
                specific_date = start.toDbValue(),
                specific_date_ = endExclusive.toDbValue(),
            )
            restQueries.deleteBetween(
                date = start.toDbValue(),
                date_ = endExclusive.toDbValue(),
            )
            routineDates.forEach { (date, routineId) ->
                queries.insertOnceIgnore(routine_id = routineId, specific_date = date.toDbValue())
            }
            restDates.forEach { restQueries.insert(date = it.toDbValue()) }
        }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        queries.updateEnabled(enabled = if (enabled) 1L else 0L, id = id)
    }

    override suspend fun deleteById(id: Long) {
        queries.deleteById(id)
    }

    override suspend fun deleteByRoutineId(routineId: Long) {
        queries.deleteByRoutineId(routineId)
    }
}
