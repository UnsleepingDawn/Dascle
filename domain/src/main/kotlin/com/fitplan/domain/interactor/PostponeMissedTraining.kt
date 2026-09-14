package com.fitplan.domain.interactor

import com.fitplan.domain.repository.ScheduleRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 顺延：把这些没练的计划挪到之后，今天及以后的安排一并往后推。
 *
 * 后移的天数取「今天 - 整段起点」，所以漏掉的计划正好落进 `[今天, 今天 + offset)`，
 * 今天的原安排落到 `今天 + offset`。
 */
@Inject
class PostponeMissedTraining(
    private val scheduleRepository: ScheduleRepository,
) {

    suspend operator fun invoke(missed: MissedTraining) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        scheduleRepository.postponeMissedPlans(
            from = missed.from,
            today = today,
            missedRoutines = missed.days.associate { it.date to it.routineIds },
        )
    }
}
