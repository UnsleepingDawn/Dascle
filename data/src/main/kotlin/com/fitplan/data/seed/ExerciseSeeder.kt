package com.fitplan.data.seed

import android.content.Context
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.fitplan.core.common.util.system.logcat
import com.fitplan.data.Database
import com.fitplan.data.mapper.toDbValue
import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.repository.ExerciseSeedRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import kotlin.time.Clock

@Serializable
internal data class SeedPayload(
    val version: Long,
    val exercises: List<SeedExercise>,
)

@Serializable
internal data class SeedExercise(
    val name: String,
    val muscleGroup: String,
    /** 次部位，缺省为空；取值非法或与主部位重复的会被忽略。 */
    val secondaryMuscleGroups: List<String> = emptyList(),
    val equipment: String,
    val description: String = "",
)

/**
 * 从 `assets/exercises.json` 读入内置动作库并写入 `exercise` 表。
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ExerciseSeeder(
    private val context: Context,
    private val database: Database,
    private val json: Json,
) : ExerciseSeedRepository {

    override suspend fun importSeedExercises(): Int {
        val payload = readPayload()

        val currentVersion = database.seedMetaQueries.selectVersion().awaitAsOneOrNull()
        if (currentVersion == payload.version) return 0

        // 名字 -> id，只含内置动作；用户自建动作不会被种子碰到。
        val idByName = database.exerciseQueries.selectNonCustom().awaitAsList()
            .associate { it.name to it.id }
        val createdAt = Clock.System.now().toDbValue()

        var inserted = 0
        database.transaction {
            payload.exercises.forEach { seed ->
                val muscleGroup = MuscleGroup.fromValue(seed.muscleGroup)
                val equipment = Equipment.fromValue(seed.equipment)
                if (muscleGroup == null || equipment == null) {
                    logcat(LogPriority.ERROR) {
                        "种子动作取值非法，已跳过：${seed.name} (${seed.muscleGroup}/${seed.equipment})"
                    }
                    return@forEach
                }

                val existingId = idByName[seed.name]
                val exerciseId = existingId ?: run {
                    database.exerciseQueries.insert(
                        name = seed.name,
                        muscle_group = muscleGroup.toDbValue(),
                        equipment = equipment.toDbValue(),
                        description = seed.description,
                        is_custom = 0L,
                        created_at = createdAt,
                    )
                    inserted++
                    database.utilQueries.lastInsertRowId().awaitAsOne()
                }

                // 内置换动作的次部位按种子幂等重建，升级种子时可补齐新加的次部位。
                database.exerciseSecondaryMuscleQueries.deleteByExerciseId(exerciseId)
                seed.secondaryMuscleGroups
                    .mapNotNull { MuscleGroup.fromValue(it) }
                    .filter { it != muscleGroup }
                    .distinct()
                    .forEach { secondary ->
                        database.exerciseSecondaryMuscleQueries.insertIgnore(
                            exercise_id = exerciseId,
                            muscle_group = secondary.toDbValue(),
                        )
                    }
            }

            database.seedMetaQueries.upsertVersion(payload.version)
        }

        logcat { "种子动作导入完成：新增 $inserted 条，种子版本 ${payload.version}" }
        return inserted
    }

    private suspend fun readPayload(): SeedPayload = withContext(Dispatchers.IO) {
        val text = context.assets.open(SEED_ASSET_NAME).bufferedReader().use { it.readText() }
        json.decodeFromString<SeedPayload>(text)
    }

    private companion object {
        const val SEED_ASSET_NAME = "exercises.json"
    }
}
