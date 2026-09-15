package com.fitplan.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.fitplan.data.Database
import com.fitplan.data.mapper.toDbValue
import com.fitplan.data.mapper.toDomain
import com.fitplan.domain.model.ExerciseProgressHint
import com.fitplan.domain.repository.ExerciseProgressHintRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ExerciseProgressHintRepositoryImpl(
    private val database: Database,
) : ExerciseProgressHintRepository {

    private val queries get() = database.exerciseProgressHintQueries

    override suspend fun get(exerciseId: Long): ExerciseProgressHint? =
        queries.selectByExerciseId(exerciseId).awaitAsOneOrNull()?.toDomain()

    override suspend fun upsert(hint: ExerciseProgressHint) {
        queries.upsert(
            exercise_id = hint.exerciseId,
            snooze_count = hint.snoozeCount.toLong(),
            next_remind_at = hint.nextRemindAt?.toDbValue(),
            updated_at = Clock.System.now().toDbValue(),
        )
    }

    override suspend fun clear(exerciseId: Long) {
        queries.deleteByExerciseId(exerciseId)
    }
}
