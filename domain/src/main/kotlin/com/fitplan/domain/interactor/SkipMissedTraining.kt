package com.fitplan.domain.interactor

import com.fitplan.domain.repository.ScheduleRepository
import dev.zacsweers.metro.Inject

/** 跳过：直接清掉这些没练的计划，今天按原安排继续。 */
@Inject
class SkipMissedTraining(
    private val scheduleRepository: ScheduleRepository,
) {

    suspend operator fun invoke(missed: MissedTraining) {
        scheduleRepository.deletePlansOn(missed.days.map { it.date })
    }
}
