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

    private var allExercises: List<Exercise> = emptyList()

    fun load() {
        viewModelScope.launch {
            allExercises = exerciseRepository.getAll()
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

    /** 把动作加进计划的末尾：目标值取默认的 3 组 × 10 次、休息 90 秒，并带上动作的默认重量/时长。 */
    suspend fun addToRoutine(routineId: Long, exerciseId: Long) {
        val exercise = allExercises.firstOrNull { it.id == exerciseId }
            ?: exerciseRepository.getById(exerciseId)
            ?: return
        routineRepository.addExercise(
            routineId = routineId,
            exerciseId = exerciseId,
            targetSets = DEFAULT_TARGET_SETS,
            targetReps = DEFAULT_TARGET_REPS,
            restSeconds = DEFAULT_REST_SECONDS,
            targetWeight = exercise.defaultWeight,
            targetSeconds = exercise.defaultDurationSeconds,
        )
    }

    private fun applyFilter() {
        _exercises.value = allExercises
            .filter { _muscleFilter.value == null || _muscleFilter.value in it.muscleGroups }
            .filter { _equipmentFilter.value == null || it.equipment == _equipmentFilter.value }
            .sortedWith(compareBy({ it.muscleGroup.ordinal }, { it.name }))
    }

    private companion object {
        const val DEFAULT_TARGET_SETS = 3
        const val DEFAULT_TARGET_REPS = 10
        const val DEFAULT_REST_SECONDS = 90
    }
}
