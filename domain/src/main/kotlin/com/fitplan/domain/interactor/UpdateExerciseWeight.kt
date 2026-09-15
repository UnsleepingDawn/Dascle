package com.fitplan.domain.interactor

import com.fitplan.domain.repository.ExerciseProgressHintRepository
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import dev.zacsweers.metro.Inject

/**
 * 动作重量的唯一变更出口：把「改重量」和「渐进重量提示状态」绑在一起，
 * 免得调用方只改重量却忘了清状态（或者反过来）。
 *
 * 提示状态只在「变重」时才清空——用户已经加到更重的重量了，旧重量上的暂缓 / 休眠自然失效。
 */
@Inject
class UpdateExerciseWeight(
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
    private val hintRepository: ExerciseProgressHintRepository,
) {

    /**
     * 提示弹窗点「好的！」：把动作库的默认重量与所有计划编排的目标重量一起抬到 [newWeight]，
     * 并清空提示状态，下次在这个更重的重量上做满还能重新提醒。
     */
    suspend fun applyFromHint(exerciseId: Long, newWeight: Double) {
        exerciseRepository.updateDefaultWeight(exerciseId, newWeight)
        routineRepository.updateTargetWeightForExercise(exerciseId, newWeight)
        hintRepository.clear(exerciseId)
    }

    /**
     * 用户手动改了某动作的目标重量：只有调高才算「主动加重量」，解除该动作的暂缓 / 休眠；
     * 调低或持平保持原状态，免得随手一改就把提醒重新打开。
     *
     * [previousWeight] 是改动前的目标重量，为空时回退到动作库的默认重量作为基准。
     */
    suspend fun onManualWeightChanged(
        exerciseId: Long,
        previousWeight: Double?,
        newWeight: Double?,
    ) {
        if (newWeight == null) return
        val baseline = previousWeight ?: exerciseRepository.getById(exerciseId)?.defaultWeight ?: return
        if (newWeight > baseline) hintRepository.clear(exerciseId)
    }
}
