package com.fitplan.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.fitplan.data.Database
import com.fitplan.data.mapper.toDbValue
import com.fitplan.data.mapper.toDomain
import com.fitplan.domain.interactor.shiftBackAfterRemovedDates
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

    override suspend fun applyComposePlan(
        start: LocalDate,
        endExclusive: LocalDate,
        routineDates: Map<LocalDate, Long>,
    ) {
        database.transactionWithResult {
            // 先清掉这一段原有的排期，再按编排写入；没写到的日子自然成为休息日。
            queries.deleteOnceBetween(
                specific_date = start.toDbValue(),
                specific_date_ = endExclusive.toDbValue(),
            )
            routineDates.forEach { (date, routineId) ->
                queries.insertOnceIgnore(routine_id = routineId, specific_date = date.toDbValue())
            }
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

            // 3. 漏掉的计划落到 date + offset 上，正好填满 [今天, 今天 + offset)。
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
            // 1. 先把「from 及以后」的排期读出来，enabled 原样保留。
            val upcoming = queries.selectOnceFrom(specific_date = from.toDbValue()).awaitAsList()

            // 2. 从今天起整段清掉，腾出来的位置正好由搬过来的安排补上；
            //    没被补上的日子成了空档，也就是休息日。
            queries.deleteOnceFrom(specific_date = start.toDbValue())

            // 3. 整体前移 offset 天：from 那天的安排落到今天，练 / 休节奏连续衔接。
            upcoming.forEach { entry ->
                queries.insertOnceWithEnabled(
                    routine_id = entry.routine_id,
                    specific_date = requireNotNull(entry.specific_date) - offset,
                    enabled = entry.enabled,
                )
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

    override suspend fun deletePlansAndShiftBack(dates: Collection<LocalDate>) {
        val removed = dates.toSet()
        val firstChanged = removed.minOrNull() ?: return
        val removedEpochDays = removed.mapTo(mutableSetOf()) { it.toEpochDays() }
        database.transactionWithResult {
            // 只有晚于被删日的排期才会前移，所以从最早被删的那天读起即可。
            val upcoming = queries.selectOnceFrom(specific_date = firstChanged.toDbValue()).awaitAsList()
            val entryByDate = upcoming.associateBy { requireNotNull(it.specific_date) }
            val moves = shiftBackAfterRemovedDates(
                // 被选中的排期本身要删掉，不参与挪位映射（被选的休息日本来就不在这里面）。
                scheduledDates = entryByDate.keys
                    .filterNot { it in removedEpochDays }
                    .sorted()
                    .map(LocalDate::fromEpochDays),
                removedDates = removed,
            )
            // 先把被删的排期与要挪位的原位置一起清掉，再写到新位置，避免撞上 specific_date 的唯一索引。
            (removed + moves.map { it.first }).forEach { date ->
                queries.deleteOnceOnDate(specific_date = date.toDbValue())
            }
            moves.forEach { (from, to) ->
                val entry = entryByDate.getValue(from.toDbValue())
                queries.insertOnceWithEnabled(
                    routine_id = entry.routine_id,
                    specific_date = to.toDbValue(),
                    enabled = entry.enabled,
                )
            }
        }
    }

    override suspend fun insertRestDaysBefore(date: LocalDate, count: Int) {
        if (count <= 0) return
        database.transactionWithResult {
            // [date] 及以后的排期整体后移 count 天；腾出来的 [date, date + count) 不写排期，即休息日。
            val upcoming = queries.selectOnceFrom(specific_date = date.toDbValue()).awaitAsList()
            queries.deleteOnceFrom(specific_date = date.toDbValue())
            upcoming.forEach { entry ->
                queries.insertOnceWithEnabled(
                    routine_id = entry.routine_id,
                    specific_date = requireNotNull(entry.specific_date) + count,
                    enabled = entry.enabled,
                )
            }
        }
    }

    override suspend fun insertPlanBefore(routineId: Long, date: LocalDate) {
        database.transactionWithResult {
            // [date] 及以后的排期整体后移一天，原本这天的安排顺移到次日。
            val upcoming = queries.selectOnceFrom(specific_date = date.toDbValue()).awaitAsList()
            queries.deleteOnceFrom(specific_date = date.toDbValue())
            upcoming.forEach { entry ->
                queries.insertOnceWithEnabled(
                    routine_id = entry.routine_id,
                    specific_date = requireNotNull(entry.specific_date) + 1,
                    enabled = entry.enabled,
                )
            }
            // 新计划落在插入的这一天。
            queries.insert(
                routine_id = routineId,
                specific_date = date.toDbValue(),
                enabled = 1L,
            )
        }
    }
}
