package com.fitplan.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.fitplan.data.Database
import com.fitplan.data.mapper.toDbValue
import com.fitplan.data.mapper.toDomain
import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
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
    private val utilQueries get() = database.utilQueries

    override suspend fun getAll(): List<Exercise> = queries.selectAll().awaitAsList().map { it.toDomain() }

    override suspend fun getById(id: Long): Exercise? = queries.selectById(id).awaitAsOneOrNull()?.toDomain()

    override suspend fun getByIds(ids: List<Long>): List<Exercise> {
        if (ids.isEmpty()) return emptyList()
        return queries.selectByIds(ids).awaitAsList().map { it.toDomain() }
    }

    override suspend fun getByMuscleGroup(muscleGroup: MuscleGroup): List<Exercise> =
        queries.selectByMuscleGroup(muscleGroup.toDbValue()).awaitAsList().map { it.toDomain() }

    override suspend fun getCustom(): List<Exercise> = queries.selectCustom().awaitAsList().map { it.toDomain() }

    override suspend fun getNonCustomNames(): Set<String> = queries.selectNonCustomNames().awaitAsList().toSet()

    override suspend fun count(): Long = queries.countAll().awaitAsOneOrNull() ?: 0L

    override suspend fun insert(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
        description: String,
        isCustom: Boolean,
        createdAt: Instant,
    ): Long = database.transactionWithResult {
        queries.insert(
            name = name,
            muscle_group = muscleGroup.toDbValue(),
            equipment = equipment.toDbValue(),
            description = description,
            is_custom = if (isCustom) 1L else 0L,
            created_at = createdAt.toDbValue(),
        )
        utilQueries.lastInsertRowId().awaitAsOne()
    }

    override suspend fun update(exercise: Exercise) {
        queries.update(
            name = exercise.name,
            muscle_group = exercise.muscleGroup.toDbValue(),
            equipment = exercise.equipment.toDbValue(),
            description = exercise.description,
            id = exercise.id,
        )
    }

    override suspend fun deleteById(id: Long) {
        queries.deleteById(id)
    }
}
