package com.fitplan.domain.interactor

import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.WorkoutSetDraft
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.WorkoutRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/**
 * 给已经过去的一天补记一次训练：直接写成一次已结束的训练，而不是排期。
 *
 * 组数按计划目标铺满并全部记为已完成，所以日历格子里能照常看到练到的肌群、统计页也算数；
 * 补记的时间未知，`finished_at` 与 `started_at` 取同一天零点，统计页对这种零时长不显示时长。
 *
 * 一天只记一次训练：这一天已经有训练记录时直接跳过，免得绕过界面重复补记。
 */
@Inject
class RecordPastWorkout(
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
) {

    suspend operator fun invoke(routineId: Long, date: LocalDate) {
        val zone = TimeZone.currentSystemDefault()
        val dayStart = date.atStartOfDayIn(zone)
        val dayEnd = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        if (workoutRepository.getSessionsBetween(dayStart, dayEnd).isNotEmpty()) return

        val routine = routineRepository.getById(routineId) ?: return
        val exercises = routineRepository.getExercises(routineId)
        workoutRepository.insertFinishedSession(
            routineId = routine.id,
            name = routine.name,
            startedAt = dayStart,
            finishedAt = dayStart,
            sets = exercises.flatMap { it.toSetDrafts() },
        )
    }
}

/** 按计划目标把动作铺成整组：计时类记时长，计数类记默认重量与次数。 */
private fun RoutineExercise.toSetDrafts(): List<WorkoutSetDraft> = List(targetSets) { index ->
    WorkoutSetDraft(
        exerciseId = exerciseId,
        setIndex = index + 1,
        weight = if (isTimed) null else targetWeight,
        reps = if (isTimed) null else targetReps,
        durationSeconds = if (isTimed) targetSeconds else null,
    )
}
