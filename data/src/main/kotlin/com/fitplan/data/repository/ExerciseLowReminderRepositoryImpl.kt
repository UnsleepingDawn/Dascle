package com.fitplan.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.fitplan.data.Database
import com.fitplan.domain.repository.ExerciseLowReminderRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ExerciseLowReminderRepositoryImpl(
    private val database: Database,
) : ExerciseLowReminderRepository {

    private val queries get() = database.exerciseLowReminderQueries

    override suspend fun isShown(exerciseId: Long, versionCode: Long): Boolean =
        queries.selectByExerciseId(exerciseId).awaitAsOneOrNull() == versionCode

    override suspend fun markShown(exerciseId: Long, versionCode: Long) {
        queries.upsert(exercise_id = exerciseId, version_code = versionCode)
    }
}
