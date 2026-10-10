package com.fitplan.domain.repository

import com.fitplan.domain.model.ScheduleEntry
import kotlinx.datetime.LocalDate

interface ScheduleRepository {

    suspend fun getAll(): List<ScheduleEntry>

    suspend fun getEnabledForDate(date: LocalDate): List<ScheduleEntry>

    /**
     * 把 [routineId] 排到 [date]，并返回新排期的 id。
     *
     * 一天只能有一个训练计划，所以这里等价于「替换该日的排期」：这一天原来排的是别的计划，
     * 会被直接顶掉（`schedule_entry.specific_date` 上有唯一索引）。
     */
    suspend fun replacePlanOn(routineId: Long, date: LocalDate): Long

    suspend fun setEnabled(id: Long, enabled: Boolean)

    suspend fun deleteById(id: Long)

    suspend fun deleteByRoutineId(routineId: Long)

    /**
     * 把编排批量铺到日历上：先清掉 `[start, endExclusive)` 内已有的排期，
     * 再按 [routineDates] 把这些天写成训练日；没写到的日子就是休息日。
     *
     * 整批放在一个事务里——中途出错或被打断时回滚，不会留下铺了一半的日历。
     */
    suspend fun applyComposePlan(
        start: LocalDate,
        endExclusive: LocalDate,
        routineDates: Map<LocalDate, Long>,
    )

    /**
     * 顺延漏练：`[from, today)` 里 [missedRoutines] 这些天的排期清掉后写到 `date + offset` 上
     * （`offset = today - from`），同时把「[today] 及以后」的排期整体后移 `offset` 天。
     *
     * 结果为：漏掉的计划正好落在 `[today, today + offset)`，今天的原安排推到 `today + offset`。
     * 整批放在一个事务里，中途出错不会留下挪了一半的日历。
     */
    suspend fun postponeMissedPlans(
        from: LocalDate,
        today: LocalDate,
        missedRoutines: Map<LocalDate, List<Long>>,
    )

    /**
     * 整体提前：把 [from] 及以后的排期往前搬 `from - start` 天，并清掉 `[start, from)`
     * 这一段原本的内容；搬完留下的空档就是休息日。
     *
     * 结果是 [from] 那天的安排落到 [start]（今天），它原本的位置正好留给明天，练 / 休节奏连续衔接。
     * 整批放在一个事务里，中途出错不会留下挪了一半的日历。
     */
    suspend fun advanceScheduleTo(start: LocalDate, from: LocalDate)

    /** 清掉 [dates] 这些天的排期（这些天随即变成休息日）。 */
    suspend fun deletePlansOn(dates: Collection<LocalDate>)

    /**
     * 压缩删除：把 [dates] 这些训练日的排期清掉，并让它们之后的训练日依次前顶进腾出来的位置，
     * 末尾多出休息日；休息日自身不动。
     *
     * 例：`10/12、10/14、10/16` 只删 `10/14`，结果是 `10/12、10/14（原 10/16）`，`10/16` 变休息。
     * 整批放在一个事务里，中途出错不会留下删了一半、没顶上的日历。
     */
    suspend fun deleteAndCompactPlans(dates: Collection<LocalDate>)

    /**
     * 在 [date] 之前插入 [count] 天休息：[date] 及以后的排期整体后移 [count] 天，
     * 腾出来的 `[date, date + count)` 就是休息日。整批放在一个事务里。
     */
    suspend fun insertRestDaysBefore(date: LocalDate, count: Int)

    /**
     * 在 [date] 之前插入一个训练日：[date] 及以后的排期整体后移一天，
     * [routineId] 落在 [date]，它原本的安排顺移到次日。整批放在一个事务里。
     */
    suspend fun insertPlanBefore(routineId: Long, date: LocalDate)

    /**
     * 今日休息：把 [date] 及以后的排期整体后移一天，之后每一天都顺延一格；
     * [date] 腾空后自然成为休息日。整批放在一个事务里。
     */
    suspend fun postponeAllFrom(date: LocalDate)

    /**
     * 今日休息：只把 `[date, restDay)` 内的排期后移一天，让 [restDay] 被训练占用，
     * [restDay] 之后的排期不动；[date] 腾空后成为休息日。整批放在一个事务里。
     */
    suspend fun postponeUntilRestDay(date: LocalDate, restDay: LocalDate)
}
