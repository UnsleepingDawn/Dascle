package com.fitplan.domain.interactor

import com.fitplan.domain.repository.ScheduleRepository
import com.fitplan.domain.repository.WorkoutRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** 漏掉的一天：这天原本要练的计划。 */
data class MissedTrainingDay(
    val date: LocalDate,
    val routineIds: List<Long>,
)

/**
 * 「上次训练之后到今天之前」这段里，安排了训练却没有任何记录的部分。
 *
 * [from] 是整段的起点（上次健身次日 / 上次已处理日的次日），不一定是第一个漏练日——
 * 顺延要按它算整体后移多少天。
 */
data class MissedTraining(
    val from: LocalDate,
    val days: List<MissedTrainingDay>,
) {
    /** 第一个没练的日子，弹窗显示的起始日期。 */
    val firstDay: LocalDate get() = days.first().date

    /** 最后一个没练的日子，弹窗显示的结束日期。 */
    val lastDay: LocalDate get() = days.last().date

    val dayCount: Int get() = days.size
}

/**
 * 检查「最近一次训练之后」有没有安排了训练却没练的日子。
 *
 * 某天要同时满足这几条才算漏练：在扫描区间内、当天没有任何训练记录（不看训练有没有结束）、
 * 不是休息日、当天有启用的排期。
 *
 * [handledUntil] 是用户上次处理到哪一天（null 表示从未处理过），它之前的日子不再检查。
 * 完全没有训练记录时返回 null——全新安装不该被问「这段时间怎么没练」。
 */
@Inject
class GetMissedTraining(
    private val scheduleRepository: ScheduleRepository,
    private val workoutRepository: WorkoutRepository,
) {

    suspend operator fun invoke(handledUntil: LocalDate?): MissedTraining? {
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(zone).date
        val lastTrained = workoutRepository.getLatestSession()
            ?.startedAt
            ?.toLocalDateTime(zone)
            ?.date
            ?: return null

        val earliest = today.minus(MAX_LOOKBACK_DAYS, DateTimeUnit.DAY)
        val from = maxOf(
            lastTrained.plus(1, DateTimeUnit.DAY),
            handledUntil?.plus(1, DateTimeUnit.DAY) ?: earliest,
            earliest,
        )
        if (from >= today) return null

        // 一次取完区间内的排期，避免按天打数据库。
        val plansByDate = scheduleRepository.getAll()
            .filter { it.enabled && it.specificDate != null }
            .groupBy { it.specificDate }
        val restDays = scheduleRepository.getRestDaysBetween(from, today).toSet()
        val trainedDates = workoutRepository
            .getSessionsBetween(from.atStartOfDayIn(zone), today.atStartOfDayIn(zone))
            .map { it.startedAt.toLocalDateTime(zone).date }
            .toSet()

        val days = generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it < today }
            .mapNotNull { date ->
                if (date in trainedDates || date in restDays) return@mapNotNull null
                val routineIds = plansByDate[date].orEmpty().map { it.routineId }.distinct()
                if (routineIds.isEmpty()) return@mapNotNull null
                MissedTrainingDay(date = date, routineIds = routineIds)
            }
            .toList()

        return if (days.isEmpty()) null else MissedTraining(from = from, days = days)
    }

    private companion object {
        /** 最多往回看这么多天：三个月没开 App 时，「顺延 300 天」不是用户想要的。 */
        const val MAX_LOOKBACK_DAYS = 60
    }
}
