package com.fitplan.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.interactor.GetWorkoutStats
import com.fitplan.domain.model.WorkoutStats
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
class StatsScreenModel(
    private val getWorkoutStats: GetWorkoutStats,
) : ViewModel() {

    private val _days = MutableStateFlow(RANGES.first())
    val days: StateFlow<Int> = _days.asStateFlow()

    private val _stats = MutableStateFlow<WorkoutStats?>(null)
    val stats: StateFlow<WorkoutStats?> = _stats.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** 重量进步图上选中的动作，默认取区间内数据最多的那个。 */
    private val _selectedExerciseId = MutableStateFlow<Long?>(null)
    val selectedExerciseId: StateFlow<Long?> = _selectedExerciseId.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val stats = getWorkoutStats(_days.value)
            _stats.value = stats
            if (stats.exerciseProgress.none { it.exerciseId == _selectedExerciseId.value }) {
                _selectedExerciseId.value = stats.exerciseProgress.firstOrNull()?.exerciseId
            }
            _loaded.value = true
        }
    }

    fun selectDays(days: Int) {
        if (_days.value == days) return
        _days.value = days
        refresh()
    }

    fun selectExercise(exerciseId: Long) {
        _selectedExerciseId.value = exerciseId
    }

    companion object {
        /** 区间切换的两个选项：天数。 */
        val RANGES = listOf(7, 30)
    }
}
