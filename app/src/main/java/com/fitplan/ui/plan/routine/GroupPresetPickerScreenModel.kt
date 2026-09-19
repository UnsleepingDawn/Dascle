package com.fitplan.ui.plan.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.ui.exercise.defaultTargets
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 一个预设动作组，以及它在当前计划里还能加入哪些动作（计划里已经排过的会被跳过）。 */
data class GroupPresetItem(
    val preset: GroupPreset,
    val addable: List<Exercise>,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class GroupPresetPickerScreenModel(
    private val presetCatalog: GroupPresetCatalog,
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
) : ViewModel() {

    /** 当前部位筛选下的预设组，顺序与 `group_presets.json` 一致。 */
    private val _presets = MutableStateFlow<List<GroupPresetItem>>(emptyList())
    val presets: StateFlow<List<GroupPresetItem>> = _presets.asStateFlow()

    /** 筛选行里要展示的部位：只列预设里真正出现过的，按 [MuscleGroup] 自带顺序。 */
    private val _muscleOptions = MutableStateFlow<List<MuscleGroup>>(emptyList())
    val muscleOptions: StateFlow<List<MuscleGroup>> = _muscleOptions.asStateFlow()

    /** `null` 表示「全部部位」。 */
    private val _muscleFilter = MutableStateFlow<MuscleGroup?>(null)
    val muscleFilter: StateFlow<MuscleGroup?> = _muscleFilter.asStateFlow()

    private var allItems: List<GroupPresetItem> = emptyList()

    fun load(routineId: Long) {
        viewModelScope.launch {
            val existingIds = routineRepository.getExercises(routineId).map { it.exerciseId }.toSet()
            // 预设按名字指向内置动作；自建动作同名时不算命中。
            val builtInByName = exerciseRepository.getAll()
                .filterNot { it.isCustom }
                .associateBy { it.name }
            allItems = presetCatalog.getPresets().map { preset ->
                GroupPresetItem(
                    preset = preset,
                    addable = preset.exerciseNames
                        .mapNotNull(builtInByName::get)
                        .filterNot { it.id in existingIds },
                )
            }
            _muscleOptions.value = MuscleGroup.entries.filter { muscle ->
                allItems.any { it.preset.muscleGroup == muscle }
            }
            applyFilter()
        }
    }

    fun setMuscleFilter(muscleGroup: MuscleGroup?) {
        _muscleFilter.value = muscleGroup
        applyFilter()
    }

    /**
     * 把预设组整个加进计划：先建一个带名字的组，再把没排过的组成动作依次追加进去。
     * 每个动作的目标值沿用动作库默认（3 组 / 休息 90 秒 / 默认重量或时长）。
     */
    suspend fun addPreset(routineId: Long, item: GroupPresetItem) {
        val members = item.addable
        if (members.isEmpty()) return
        // 「做其中 x 个」不能超过实际加进来的动作数，否则训练页永远挑不满。
        val groupId = routineRepository.addGroup(
            routineId = routineId,
            maxPicks = minOf(item.preset.maxPicks, members.size),
            name = item.preset.name,
        )
        members.forEach { exercise ->
            val targets = exercise.defaultTargets()
            routineRepository.addExerciseToGroup(
                groupId = groupId,
                exerciseId = exercise.id,
                targetSets = targets.sets,
                targetReps = targets.reps,
                restSeconds = targets.restSeconds,
                targetWeight = targets.weight,
                targetSeconds = targets.seconds,
            )
        }
    }

    private fun applyFilter() {
        _presets.value = allItems.filter { item ->
            _muscleFilter.value == null || item.preset.muscleGroup == _muscleFilter.value
        }
    }
}
