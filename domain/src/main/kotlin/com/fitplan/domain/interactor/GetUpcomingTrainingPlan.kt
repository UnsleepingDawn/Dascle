package com.fitplan.domain.interactor

import com.fitplan.domain.repository.ScheduleRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.LocalDate

/**
 * [today] 之后最近的一次训练安排，用于休息日「使用明天的方案」。
 *
 * [dayOffset] 是它离今天还有几天：把这段排期整体提前这么多天，今天就能直接练上，
 * 而它原本的位置正好落给明天。
 */
data class UpcomingTrainingPlan(
    val date: LocalDate,
    val routineId: Long,
    val routineName: String,
    val dayOffset: Int,
)

/**
 * 找今天之后最近的一个训练日。之后完全没有启用排期时返回 null——
 * 那种情况下「使用明天的方案」没有内容可搬，只能走临时加方案。
 */
@Inject
class GetUpcomingTrainingPlan(
    private val scheduleRepository: ScheduleRepository,
) {

    suspend operator fun invoke(today: LocalDate): UpcomingTrainingPlan? {
        val entry = scheduleRepository.getAll()
            .filter { it.enabled && it.specificDate != null && it.specificDate > today }
            .minByOrNull { requireNotNull(it.specificDate) }
            ?: return null
        val date = requireNotNull(entry.specificDate)

        return UpcomingTrainingPlan(
            date = date,
            routineId = entry.routineId,
            routineName = entry.routineName,
            dayOffset = (date.toEpochDays() - today.toEpochDays()).toInt(),
        )
    }
}
