package com.fitplan.domain.interactor

import com.fitplan.domain.repository.ScheduleRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** 「今日休息」把今天改成休息日时，之后的排期怎么顺延。 */
enum class RestTodayMode {
    /** 之后每一天都顺延一格：今天及以后整体后移一天。 */
    POSTPONE_ALL,

    /** 只顺延到下一个休息日：让那个休息日被训练占用，之后的安排不动。 */
    POSTPONE_UNTIL_REST_DAY,
}

/**
 * 今日休息：把今天改成休息日，并按 [mode] 顺延之后的排期。
 *
 * 「顺延直到占用下一个休息日」没有更晚的休息日可占用时什么都不做——界面本来就把这个选项
 * 置灰了，这里只是兜底，免得它悄悄退化成「顺延之后每一天」。
 */
@Inject
class TakeRestToday(
    private val scheduleRepository: ScheduleRepository,
) {

    suspend operator fun invoke(mode: RestTodayMode) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        when (mode) {
            RestTodayMode.POSTPONE_ALL -> scheduleRepository.postponeAllFrom(today)
            RestTodayMode.POSTPONE_UNTIL_REST_DAY -> {
                val restDay = scheduleRepository.getNextRestDay(today) ?: return
                scheduleRepository.postponeUntilRestDay(today, restDay)
            }
        }
    }
}
