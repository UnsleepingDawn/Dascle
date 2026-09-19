package com.fitplan.ui.plan.routine

import android.content.Context
import com.fitplan.core.common.util.system.logcat
import com.fitplan.domain.model.MuscleGroup
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority

/** 一个预设动作组：组里的动作可以互相替换，训练时挑最多 [maxPicks] 个来练。 */
data class GroupPreset(
    val name: String,
    val muscleGroup: MuscleGroup,
    val maxPicks: Int,
    /** 组内动作在动作库里的名字，加入计划时按名字解析成具体动作。 */
    val exerciseNames: List<String>,
)

/**
 * 读 `assets/group_presets.json` 里的预设动作组。
 *
 * 预设是内置数据，只在这里读；加入计划后就和手动建的动作组没有区别。
 */
@Inject
@SingleIn(AppScope::class)
class GroupPresetCatalog(
    private val context: Context,
    private val json: Json,
) {

    suspend fun getPresets(): List<GroupPreset> = withContext(Dispatchers.IO) {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        json.decodeFromString<PresetPayload>(text).groups.mapNotNull { it.toDomain() }
    }

    private companion object {
        const val ASSET_NAME = "group_presets.json"
    }
}

@Serializable
private data class PresetPayload(val groups: List<PresetGroup> = emptyList())

@Serializable
private data class PresetGroup(
    val name: String,
    val muscleGroup: String,
    val maxPicks: Int = 1,
    val exerciseNames: List<String> = emptyList(),
)

/** 部位取值非法或组里没写动作时跳过这条预设，并记一条错误日志。 */
private fun PresetGroup.toDomain(): GroupPreset? {
    val muscle = MuscleGroup.fromValue(muscleGroup)
    if (muscle == null) {
        logcat(LogPriority.ERROR) { "预设动作组部位取值非法，已跳过：$name ($muscleGroup)" }
        return null
    }
    if (exerciseNames.isEmpty()) {
        logcat(LogPriority.ERROR) { "预设动作组里没有动作，已跳过：$name" }
        return null
    }
    return GroupPreset(
        name = name,
        muscleGroup = muscle,
        maxPicks = maxPicks,
        exerciseNames = exerciseNames,
    )
}
