package com.fitplan.domain.repository

import com.fitplan.domain.model.Routine
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineItem
import kotlin.time.Instant

interface RoutineRepository {

    suspend fun getAll(): List<Routine>

    suspend fun getById(id: Long): Routine?

    suspend fun count(): Long

    suspend fun insert(name: String, note: String, createdAt: Instant): Long

    suspend fun update(id: Long, name: String, note: String)

    suspend fun deleteById(id: Long)

    /** 计划内的全部动作（含动作组里的），按各自的 `position` 排序。 */
    suspend fun getExercises(routineId: Long): List<RoutineExercise>

    /** 计划编辑页顶层的编排：单独排列的动作与动作组按共用序号交错排列，组内已含动作。 */
    suspend fun getItems(routineId: Long): List<RoutineItem>

    suspend fun addExercise(
        routineId: Long,
        exerciseId: Long,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        targetWeight: Double? = null,
        targetSeconds: Int? = null,
    ): Long

    suspend fun updateExerciseTargets(
        id: Long,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        targetWeight: Double? = null,
        targetSeconds: Int? = null,
    )

    /** 按传入的顺序重写顶层 `position`，供拖拽排序使用（动作与动作组混在一起）。 */
    suspend fun reorderItems(routineId: Long, orderedItems: List<RoutineItem>)

    /** 在计划末尾新建一个动作组，返回新组 id；[name] 为空表示还没起名字。 */
    suspend fun addGroup(routineId: Long, maxPicks: Int = DEFAULT_MAX_PICKS, name: String? = null): Long

    /** 设「做其中 x 个」；写入前按组内动作数收敛（1..组内动作数）。 */
    suspend fun updateGroupMaxPicks(groupId: Long, maxPicks: Int)

    /** 改动作组的名字；传空字符串 / null 表示清掉名字、回到默认的「动作组」。 */
    suspend fun updateGroupName(groupId: Long, name: String?)

    /** 删除动作组，组内动作一并删除。 */
    suspend fun removeGroup(groupId: Long)

    /** 把动作追加到动作组的末尾。 */
    suspend fun addExerciseToGroup(
        groupId: Long,
        exerciseId: Long,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        targetWeight: Double? = null,
        targetSeconds: Int? = null,
    )

    /** 把某个动作在**所有**计划里的目标重量一起改成 [weight]，供渐进提示使用。 */
    suspend fun updateTargetWeightForExercise(exerciseId: Long, weight: Double)

    /** 把某个动作在**所有**计划里的目标次数一起改成 [reps]，供渐进提示使用。 */
    suspend fun updateTargetRepsForExercise(exerciseId: Long, reps: Int)

    /** 把某个动作在**所有**计划里的目标时长一起改成 [seconds]，供渐进提示使用。 */
    suspend fun updateTargetSecondsForExercise(exerciseId: Long, seconds: Int)

    suspend fun removeExercise(id: Long)

    companion object {
        /** 新动作组默认「做其中 1 个」。 */
        const val DEFAULT_MAX_PICKS = 1
    }
}
