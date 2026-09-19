package com.fitplan.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ExercisePickerScreenModel(
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
) : ViewModel() {

    private val _exercises = MutableStateFlow<List<Exercise>>(emptyList())
    val exercises: StateFlow<List<Exercise>> = _exercises.asStateFlow()

    /** `null` 表示「全部肌群」。 */
    private val _muscleFilter = MutableStateFlow<MuscleGroup?>(null)
    val muscleFilter: StateFlow<MuscleGroup?> = _muscleFilter.asStateFlow()

    /** `null` 表示「全部器械」。 */
    private val _equipmentFilter = MutableStateFlow<Equipment?>(null)
    val equipmentFilter: StateFlow<Equipment?> = _equipmentFilter.asStateFlow()

    /** 往动作组里加动作时的多选结果（按动作库顺序即点击顺序）。 */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    /** true 表示动作库里已经没有可加的动作了：计划的每个动作都排过了。 */
    private val _allAdded = MutableStateFlow(false)
    val allAdded: StateFlow<Boolean> = _allAdded.asStateFlow()

    private var allExercises: List<Exercise> = emptyList()

    /**
     * 读动作库，并排除这个计划里已经排过的动作：同一个动作在计划里只出现一次，
     * 否则记录页会把它当成两个动作分别记录。
     */
    fun load(routineId: Long) {
        viewModelScope.launch {
            val existingIds = routineRepository.getExercises(routineId).map { it.exerciseId }.toSet()
            allExercises = exerciseRepository.getAll().filterNot { it.id in existingIds }
            _allAdded.value = allExercises.isEmpty()
            applyFilter()
        }
    }

    fun setMuscleFilter(muscleGroup: MuscleGroup?) {
        _muscleFilter.value = muscleGroup
        applyFilter()
    }

    fun setEquipmentFilter(equipment: Equipment?) {
        _equipmentFilter.value = equipment
        applyFilter()
    }

    /** 组模式：勾选 / 取消勾选一个动作。 */
    fun toggleSelect(exerciseId: Long) {
        _selectedIds.value = _selectedIds.value.let { current ->
            if (exerciseId in current) current - exerciseId else current + exerciseId
        }
    }

    /**
     * 把动作单独加进计划的末尾：目标值取默认的 3 组 × 10 次、休息 90 秒，
     * 并按动作类型带上动作库的默认重量（辅助类为辅助重量）或默认时长。
     */
    suspend fun addToRoutine(routineId: Long, exerciseId: Long) {
        val exercise = findExercise(exerciseId) ?: return
        val targets = exercise.defaultTargets()
        routineRepository.addExercise(
            routineId = routineId,
            exerciseId = exerciseId,
            targetSets = targets.sets,
            targetReps = targets.reps,
            restSeconds = targets.restSeconds,
            targetWeight = targets.weight,
            targetSeconds = targets.seconds,
        )
    }

    /** 组模式：把勾选的动作依次追加到动作组末尾，动作组里的顺序就是加入顺序。 */
    suspend fun addSelectedToGroup(groupId: Long) {
        val selected = _selectedIds.value
        allExercises.filter { it.id in selected }.forEach { exercise ->
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
        _selectedIds.value = emptySet()
    }

    private suspend fun findExercise(exerciseId: Long): Exercise? =
        allExercises.firstOrNull { it.id == exerciseId } ?: exerciseRepository.getById(exerciseId)

    private fun applyFilter() {
        _exercises.value = allExercises
            .filter { _muscleFilter.value == null || _muscleFilter.value in it.muscleGroups }
            .filter { _equipmentFilter.value == null || it.equipment == _equipmentFilter.value }
            .sortedWith(compareBy({ it.muscleGroup.ordinal }, { it.name }))
    }
}
