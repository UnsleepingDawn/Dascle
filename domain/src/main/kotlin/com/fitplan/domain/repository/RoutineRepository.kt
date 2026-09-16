package com.fitplan.domain.repository

import com.fitplan.domain.model.Routine
import com.fitplan.domain.model.RoutineExercise
import kotlin.time.Instant

interface RoutineRepository {

    suspend fun getAll(): List<Routine>

    suspend fun getById(id: Long): Routine?

    suspend fun count(): Long

    suspend fun insert(name: String, note: String, createdAt: Instant): Long

    suspend fun update(id: Long, name: String, note: String)

    suspend fun deleteById(id: Long)

    suspend fun getExercises(routineId: Long): List<RoutineExercise>

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

    /** 按传入的 id 顺序重写 `position`，供拖拽排序使用。 */
    suspend fun reorderExercises(routineId: Long, orderedRoutineExerciseIds: List<Long>)

    /** 把某个动作在**所有**计划里的目标重量一起改成 [weight]，供渐进提示使用。 */
    suspend fun updateTargetWeightForExercise(exerciseId: Long, weight: Double)

    /** 把某个动作在**所有**计划里的目标次数一起改成 [reps]，供渐进提示使用。 */
    suspend fun updateTargetRepsForExercise(exerciseId: Long, reps: Int)

    /** 把某个动作在**所有**计划里的目标时长一起改成 [seconds]，供渐进提示使用。 */
    suspend fun updateTargetSecondsForExercise(exerciseId: Long, seconds: Int)

    suspend fun removeExercise(id: Long)
}
