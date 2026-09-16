package com.fitplan.domain.interactor

import com.fitplan.domain.model.ExerciseLoadMode
import com.fitplan.domain.repository.ExerciseProgressHintRepository
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import dev.zacsweers.metro.Inject

/**
 * 动作目标值的唯一变更出口：把「改目标值」和「渐进提示状态」绑在一起，
 * 免得调用方只改数值却忘了清状态（或者反过来）。
 *
 * 提示状态只在「变难」时才清空——用户已经把目标抬高了，旧目标上的暂缓 / 休眠自然失效。
 */
@Inject
class UpdateExerciseProgression(
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
    private val hintRepository: ExerciseProgressHintRepository,
) {

    /**
     * 提示弹窗选了「增加重量」：把动作库的默认重量与所有计划编排的目标重量一起抬到 [newWeight]，
     * 并清空提示状态，下次在这个更重的重量上做满还能重新提醒。
     */
    suspend fun applyWeight(exerciseId: Long, newWeight: Double) {
        exerciseRepository.updateDefaultWeight(exerciseId, newWeight)
        routineRepository.updateTargetWeightForExercise(exerciseId, newWeight)
        hintRepository.clear(exerciseId)
    }

    /** 提示弹窗选了「增加次数」（自重动作）：动作库默认次数与所有计划的目标次数一起改成 [newReps]。 */
    suspend fun applyReps(exerciseId: Long, newReps: Int) {
        exerciseRepository.updateDefaultReps(exerciseId, newReps)
        routineRepository.updateTargetRepsForExercise(exerciseId, newReps)
        hintRepository.clear(exerciseId)
    }

    /** 提示弹窗选了「增加时间」（计时动作）：动作库默认时长与所有计划的目标时长一起改成 [newSeconds]。 */
    suspend fun applySeconds(exerciseId: Long, newSeconds: Int) {
        exerciseRepository.updateDefaultDuration(exerciseId, newSeconds)
        routineRepository.updateTargetSecondsForExercise(exerciseId, newSeconds)
        hintRepository.clear(exerciseId)
    }

    /**
     * 用户手动改了某动作的目标重量：只有「变难」才算主动提高目标，解除该动作的暂缓 / 休眠。
     *
     * 辅助类动作（器械辅助引体向上）的重量是助力，调**低**才更难；其余动作调高才更难。
     * 持平保持原状态，免得随手一改就把提醒重新打开。
     *
     * [previousWeight] 是改动前的目标重量，为空时回退到动作库的默认重量作为基准。
     */
    suspend fun onManualWeightChanged(
        exerciseId: Long,
        previousWeight: Double?,
        newWeight: Double?,
        loadMode: ExerciseLoadMode,
    ) {
        if (newWeight == null) return
        val baseline = previousWeight ?: exerciseRepository.getById(exerciseId)?.defaultWeight ?: return
        val harder = if (loadMode == ExerciseLoadMode.ASSISTED) {
            newWeight < baseline
        } else {
            newWeight > baseline
        }
        if (harder) hintRepository.clear(exerciseId)
    }
}
