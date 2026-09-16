package com.fitplan.domain.repository

import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseLoadMode
import com.fitplan.domain.model.ExerciseMetric
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
        metric: ExerciseMetric = ExerciseMetric.DEFAULT,
        loadMode: ExerciseLoadMode = ExerciseLoadMode.DEFAULT,
    ): Long

    suspend fun update(exercise: Exercise)

    /** 抬高 / 调整动作库里的默认重量，供渐进提示使用；辅助类动作则调低辅助重量。 */
    suspend fun updateDefaultWeight(id: Long, weight: Double)

    /** 调整动作库里的默认次数，供「增加次数」的渐进提示使用。 */
    suspend fun updateDefaultReps(id: Long, reps: Int)

    /** 调整动作库里的默认时长（秒），供「增加时间」的渐进提示使用。 */
    suspend fun updateDefaultDuration(id: Long, seconds: Int)

    suspend fun deleteById(id: Long)
}
