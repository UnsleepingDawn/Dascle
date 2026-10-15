package com.fitplan.domain.interactor

import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineItem
import com.fitplan.domain.model.WorkoutSetDraft
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.WorkoutRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.time.Instant

/**
 * 给已经过去的一天补记一次训练：直接写成一次已结束的训练，而不是排期。
 *
 * 组数按计划目标铺满并全部记为已完成，所以日历格子里能照常看到练到的肌群、统计页也算数；
 * 补记的时间未知，`finished_at` 与 `started_at` 取同一天零点，统计页对这种零时长不显示时长。
 *
 * 一天只记一次训练：这一天已经有**已结束**的训练时直接跳过，免得绕过界面重复补记。
 * 判据只看已结束的训练，与训练日历「过去的日子只认实际练到的」口径一致——若按「有没有这条
 * session」判断，重复点「开始训练」留下的空壳（未结束、一组都没勾，日历上不显示）会把补记
 * 静默挡掉，表现为「点了添加计划却还是休息」。
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
        if (workoutRepository.getFinishedSessionsBetween(dayStart, dayEnd).isNotEmpty()) return

        // 写之前先清掉这一天遗留的空壳训练：日历本来就不显示它们，留着只会继续挡住补记。
        discardEmptySessions(dayStart, dayEnd)

        val routine = routineRepository.getById(routineId) ?: return
        val exercises = routineRepository.getItems(routineId).flatMap { item ->
            when (item) {
                is RoutineItem.Exercise -> listOf(item.value)
                // 补记时自动按「建议做 x 个」取组内排在前面的 x 个，不必让用户面对整组动作。
                is RoutineItem.Group -> item.value.exercises.take(item.value.maxPicks)
            }
        }
        workoutRepository.insertFinishedSession(
            routineId = routine.id,
            name = routine.name,
            startedAt = dayStart,
            finishedAt = dayStart,
            sets = exercises.flatMap { it.toSetDrafts() },
        )
    }

    /**
     * 丢掉 [dayStart, dayEnd) 里「没结束、一组都没勾过」的空壳训练。
     *
     * 这类记录没有任何入口能再进去练（今日页只看当天，日历只显示已结束的），留着就是垃圾；
     * 勾过组的未结束训练不算空壳，它的成果要留给「收尾隔天的未完成训练」处理。
     */
    private suspend fun discardEmptySessions(dayStart: Instant, dayEnd: Instant) {
        workoutRepository.getSessionsBetween(dayStart, dayEnd)
            .filterNot { it.isFinished }
            .filter { session -> workoutRepository.getSets(session.id).none { it.completed } }
            .forEach { workoutRepository.deleteSession(it.id) }
    }
}

/**
 * 按计划目标把动作铺成整组：计时类记时长，计数类记次数；
 * 重量只在动作需要记录重量时才写（纯自重动作留空）。
 */
private fun RoutineExercise.toSetDrafts(): List<WorkoutSetDraft> = List(targetSets) { index ->
    WorkoutSetDraft(
        exerciseId = exerciseId,
        setIndex = index + 1,
        weight = if (showsWeight) targetWeight else null,
        reps = if (isTimed) null else targetReps,
        durationSeconds = if (isTimed) targetSeconds else null,
    )
}
