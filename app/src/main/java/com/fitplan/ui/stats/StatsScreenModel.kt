package com.fitplan.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.interactor.GetWorkoutStats
import com.fitplan.domain.model.StatsRange
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

/** 重量进步图的横轴显示方式。 */
enum class ProgressAxisMode {
    /** 等间距：只看练了几次，横轴均匀排布，练 1 次和练 10 次在图上一样近。 */
    INDEX,

    /** 按日期：横轴是真实日期，中间没练的日子会留出空档。 */
    DATE,
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class StatsScreenModel(
    private val getWorkoutStats: GetWorkoutStats,
) : ViewModel() {

    private val _range = MutableStateFlow(RANGES.first())
    val range: StateFlow<StatsRange> = _range.asStateFlow()

    private val _axisMode = MutableStateFlow(ProgressAxisMode.INDEX)
    val axisMode: StateFlow<ProgressAxisMode> = _axisMode.asStateFlow()

    private val _stats = MutableStateFlow<WorkoutStats?>(null)
    val stats: StateFlow<WorkoutStats?> = _stats.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** 重量进步图上选中的动作，默认取区间内数据最多的那个。 */
    private val _selectedExerciseId = MutableStateFlow<Long?>(null)
    val selectedExerciseId: StateFlow<Long?> = _selectedExerciseId.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val stats = getWorkoutStats(_range.value)
            _stats.value = stats
            if (stats.exerciseProgress.none { it.exerciseId == _selectedExerciseId.value }) {
                _selectedExerciseId.value = stats.exerciseProgress.firstOrNull()?.exerciseId
            }
            _loaded.value = true
        }
    }

    fun selectRange(range: StatsRange) {
        if (_range.value == range) return
        _range.value = range
        refresh()
    }

    fun selectAxisMode(mode: ProgressAxisMode) {
        _axisMode.value = mode
    }

    fun selectExercise(exerciseId: Long) {
        _selectedExerciseId.value = exerciseId
    }

    companion object {
        /** 区间切换的选项，顺序就是选择器里 chip 的排列顺序。 */
        val RANGES = StatsRange.entries
    }
}
