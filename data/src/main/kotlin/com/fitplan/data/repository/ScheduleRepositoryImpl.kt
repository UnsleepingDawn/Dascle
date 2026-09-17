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

    override suspend fun getEnabledForDate(date: LocalDate): List<ScheduleEntry> =
        queries.selectEnabledBySpecificDate(date.toDbValue()).awaitAsList().map { it.toDomain() }

    override suspend fun replacePlanOn(routineId: Long, date: LocalDate): Long =
        database.transactionWithResult {
            // 一天只能有一个计划：先把这一天的旧排期清掉，再写入新的那条。
            queries.deleteOnceOnDate(specific_date = date.toDbValue())
            queries.insert(
                routine_id = routineId,
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

    override suspend fun setRestDay(date: LocalDate) {
        database.transactionWithResult {
            // 一天不是训练日就是休息日：标休息的同时把这一天的排期撤掉。
            queries.deleteOnceOnDate(specific_date = date.toDbValue())
            restQueries.insert(date = date.toDbValue())
        }
    }

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

    override suspend fun postponeMissedPlans(
        from: LocalDate,
        today: LocalDate,
        missedRoutines: Map<LocalDate, List<Long>>,
    ) {
        val offset = today.toEpochDays() - from.toEpochDays()
        if (offset <= 0) return

        database.transactionWithResult {
            // 1. 漏掉那几天原本的排期清掉，等会儿按 date + offset 落到新位置上。
            missedRoutines.keys.forEach { date ->
                queries.deleteOnceOnDate(specific_date = date.toDbValue())
            }

            // 2. 「今天及以后」的排期整体后移，enabled 原样保留（停用的排期不会因此被启用）。
            val upcoming = queries.selectOnceFrom(specific_date = today.toDbValue()).awaitAsList()
            queries.deleteOnceFrom(specific_date = today.toDbValue())
            upcoming.forEach { entry ->
                queries.insertOnceWithEnabled(
                    routine_id = entry.routine_id,
                    specific_date = requireNotNull(entry.specific_date) + offset,
                    enabled = entry.enabled,
                )
            }

            // 3. 休息日跟着排期一起后移，今天的休息日才不会和搬过来的训练撞在同一天。
            val upcomingRest = restQueries.selectFrom(date = today.toDbValue()).awaitAsList()
            restQueries.deleteFrom(date = today.toDbValue())
            upcomingRest.forEach { day ->
                restQueries.insert(date = day + offset)
            }

            // 4. 漏掉区间里本来标着休息的日子也在后面补一份，保持原来的练/休节奏；
            //    原位置留着不动——那几天当初确实休息了。
            restQueries.selectBetween(
                date = from.toDbValue(),
                date_ = today.toDbValue(),
            ).awaitAsList().forEach { day ->
                restQueries.insert(date = day + offset)
            }

            // 5. 漏掉的计划落到 date + offset 上，正好填满 [今天, 今天 + offset)。
            missedRoutines.forEach { (date, routineIds) ->
                routineIds.forEach { routineId ->
                    queries.insertOnceWithEnabled(
                        routine_id = routineId,
                        specific_date = date.toDbValue() + offset,
                        enabled = 1L,
                    )
                }
            }
        }
    }

    override suspend fun advanceScheduleTo(start: LocalDate, from: LocalDate) {
        val offset = from.toEpochDays() - start.toEpochDays()
        if (offset <= 0) return

        database.transactionWithResult {
            // 1. 先把「from 及以后」的排期与休息日读出来，enabled 原样保留。
            val upcoming = queries.selectOnceFrom(specific_date = from.toDbValue()).awaitAsList()
            val upcomingRest = restQueries.selectFrom(date = from.toDbValue()).awaitAsList()

            // 2. 从今天起整段清掉：今天的休息日、今天到 from 之间的空档与休息日都不再保留，
            //    腾出来的位置正好由搬过来的安排补上。
            queries.deleteOnceFrom(specific_date = start.toDbValue())
            restQueries.deleteFrom(date = start.toDbValue())

            // 3. 整体前移 offset 天：from 那天的安排落到今天，练 / 休节奏连续衔接。
            upcoming.forEach { entry ->
                queries.insertOnceWithEnabled(
                    routine_id = entry.routine_id,
                    specific_date = requireNotNull(entry.specific_date) - offset,
                    enabled = entry.enabled,
                )
            }
            upcomingRest.forEach { day ->
                restQueries.insert(date = day - offset)
            }
        }
    }

    override suspend fun deletePlansOn(dates: Collection<LocalDate>) {
        database.transactionWithResult {
            dates.forEach { date ->
                queries.deleteOnceOnDate(specific_date = date.toDbValue())
            }
        }
    }
}
