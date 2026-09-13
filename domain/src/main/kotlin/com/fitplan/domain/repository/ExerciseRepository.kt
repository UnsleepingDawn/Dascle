package com.fitplan.domain.repository

import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.MuscleGroup
import kotlin.time.Instant

interface ExerciseRepository {

    suspend fun getAll(): List<Exercise>

    suspend fun getById(id: Long): Exercise?

    suspend fun getByIds(ids: List<Long>): List<Exercise>

    /** 主部位或次部位命中 [muscleGroup] 的动作。 */
    suspend fun getByMuscleGroup(muscleGroup: MuscleGroup): List<Exercise>

    suspend fun getCustom(): List<Exercise>

    /** 内置（非用户自建）动作的名字，用于种子数据按 [name] 去重。 */
    suspend fun getNonCustomNames(): Set<String>

    suspend fun count(): Long

    suspend fun insert(
        name: String,
        muscleGroup: MuscleGroup,
        secondaryMuscleGroups: List<MuscleGroup> = emptyList(),
        equipment: Equipment,
        description: String,
        isCustom: Boolean,
        createdAt: Instant,
    ): Long

    suspend fun update(exercise: Exercise)

    suspend fun deleteById(id: Long)
}
