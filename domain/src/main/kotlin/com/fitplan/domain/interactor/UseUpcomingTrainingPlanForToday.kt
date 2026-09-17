package com.fitplan.domain.interactor

import com.fitplan.domain.repository.ScheduleRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 把 [plan] 这一天（今天之后最近的一个训练日）的安排提到今天，并返回它的计划 id，
 * 供界面直接进入训练记录页开练。
 *
 * 之后的所有排期与休息日一并提前，练 / 休节奏原样保留；今天原本的休息日标记被撤掉。
 */
@Inject
class UseUpcomingTrainingPlanForToday(
    private val scheduleRepository: ScheduleRepository,
) {

    suspend operator fun invoke(plan: UpcomingTrainingPlan): Long {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        // 今天本来就不该有排期（否则今日页走的不是休息页），所以这里只管把之后的整体提前。
        scheduleRepository.advanceScheduleTo(start = today, from = plan.date)
        return plan.routineId
    }
}
