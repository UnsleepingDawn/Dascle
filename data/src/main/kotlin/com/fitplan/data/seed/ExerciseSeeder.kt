package com.fitplan.data.seed

import android.content.Context
import app.cash.sqldelight.async.coroutines.awaitAsList
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

        val existingNames = database.exerciseQueries.selectNonCustomNames().awaitAsList().toSet()
        val createdAt = Clock.System.now().toDbValue()

        var inserted = 0
        database.transaction {
            payload.exercises
                .filterNot { it.name in existingNames }
                .forEach { seed ->
                    val muscleGroup = MuscleGroup.fromValue(seed.muscleGroup)
                    val equipment = Equipment.fromValue(seed.equipment)
                    if (muscleGroup == null || equipment == null) {
                        logcat(LogPriority.ERROR) {
                            "种子动作取值非法，已跳过：${seed.name} (${seed.muscleGroup}/${seed.equipment})"
                        }
                        return@forEach
                    }

                    database.exerciseQueries.insertIgnore(
                        name = seed.name,
                        muscle_group = muscleGroup.toDbValue(),
                        equipment = equipment.toDbValue(),
                        description = seed.description,
                        is_custom = 0L,
                        created_at = createdAt,
                    )
                    inserted++
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
