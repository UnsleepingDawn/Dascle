package com.fitplan.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.fitplan.data.Database
import com.fitplan.data.mapper.toDbValue
import com.fitplan.data.mapper.toDomain
import com.fitplan.data.mapper.toMuscleGroup
import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseLoadMode
import com.fitplan.domain.model.ExerciseMetric
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.repository.ExerciseRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Instant

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ExerciseRepositoryImpl(
    private val database: Database,
) : ExerciseRepository {

    private val queries get() = database.exerciseQueries
    private val secondaryQueries get() = database.exerciseSecondaryMuscleQueries
    private val utilQueries get() = database.utilQueries

    override suspend fun getAll(): List<Exercise> =
        attachSecondary(queries.selectAll().awaitAsList().map { it.toDomain() })

    override suspend fun getById(id: Long): Exercise? =
        attachSecondary(queries.selectById(id).awaitAsList().map { it.toDomain() }).firstOrNull()

    override suspend fun getByIds(ids: List<Long>): List<Exercise> {
        if (ids.isEmpty()) return emptyList()
        return attachSecondary(queries.selectByIds(ids).awaitAsList().map { it.toDomain() })
    }

    override suspend fun getByMuscleGroup(muscleGroup: MuscleGroup): List<Exercise> =
        getAll().filter { muscleGroup in it.muscleGroups }

    override suspend fun getCustom(): List<Exercise> =
        attachSecondary(queries.selectCustom().awaitAsList().map { it.toDomain() })

    override suspend fun getNonCustomNames(): Set<String> = queries.selectNonCustomNames().awaitAsList().toSet()

    override suspend fun count(): Long = queries.countAll().awaitAsOneOrNull() ?: 0L

    override suspend fun insert(
        name: String,
        muscleGroup: MuscleGroup,
        secondaryMuscleGroups: List<MuscleGroup>,
        equipment: Equipment,
        description: String,
        isCustom: Boolean,
        createdAt: Instant,
        metric: ExerciseMetric,
        loadMode: ExerciseLoadMode,
    ): Long = database.transactionWithResult {
        queries.insert(
            name = name,
            muscle_group = muscleGroup.toDbValue(),
            equipment = equipment.toDbValue(),
            description = description,
            is_custom = if (isCustom) 1L else 0L,
            created_at = createdAt.toDbValue(),
            // 默认重量/时长/次数只由内置动作种子与渐进提示提供，自建动作留空。
            default_weight = null,
            default_duration_seconds = null,
            metric = metric.toDbValue(),
            load_mode = loadMode.toDbValue(),
            default_reps = null,
        )
        val id = utilQueries.lastInsertRowId().awaitAsOne()
        writeSecondary(id, muscleGroup, secondaryMuscleGroups)
        id
    }

    override suspend fun update(exercise: Exercise) {
        database.transaction {
            queries.update(
                name = exercise.name,
                muscle_group = exercise.muscleGroup.toDbValue(),
                equipment = exercise.equipment.toDbValue(),
                description = exercise.description,
                id = exercise.id,
            )
            secondaryQueries.deleteByExerciseId(exercise.id)
            writeSecondary(exercise.id, exercise.muscleGroup, exercise.secondaryMuscleGroups)
        }
    }

    override suspend fun deleteById(id: Long) {
        // exercise_secondary_muscle 由外键 ON DELETE CASCADE 清理。
        queries.deleteById(id)
    }

    override suspend fun updateDefaultWeight(id: Long, weight: Double) {
        queries.updateDefaultWeight(default_weight = weight, id = id)
    }

    override suspend fun updateDefaultReps(id: Long, reps: Int) {
        queries.updateDefaultReps(default_reps = reps.toLong(), id = id)
    }

    override suspend fun updateDefaultDuration(id: Long, seconds: Int) {
        queries.updateDefaultDuration(default_duration_seconds = seconds.toLong(), id = id)
    }

    /** 写入次部位，跳过与主部位重复的项。 */
    private suspend fun writeSecondary(
        exerciseId: Long,
        muscleGroup: MuscleGroup,
        secondaryMuscleGroups: List<MuscleGroup>,
    ) {
        secondaryMuscleGroups
            .filter { it != muscleGroup }
            .distinct()
            .forEach { secondary ->
                secondaryQueries.insertIgnore(exerciseId, secondary.toDbValue())
            }
    }

    /** 按动作 id 批量补上次部位，避免逐个动作查库。 */
    private suspend fun attachSecondary(exercises: List<Exercise>): List<Exercise> {
        if (exercises.isEmpty()) return exercises
        val secondaryByExerciseId = secondaryQueries
            .selectByExerciseIds(exercises.map { it.id })
            .awaitAsList()
            .groupBy({ it.exercise_id }, { it.toMuscleGroup() })
        return exercises.map { it.copy(secondaryMuscleGroups = secondaryByExerciseId[it.id].orEmpty()) }
    }
}
