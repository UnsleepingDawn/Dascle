package com.fitplan.ui.plan.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.widget.WidgetManager
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
class RoutineEditScreenModel(
    private val routineRepository: RoutineRepository,
    private val widgetManager: WidgetManager,
) : ViewModel() {

    private val _routineName = MutableStateFlow("")
    val routineName: StateFlow<String> = _routineName.asStateFlow()

    private val _exercises = MutableStateFlow<List<RoutineExercise>>(emptyList())
    val exercises: StateFlow<List<RoutineExercise>> = _exercises.asStateFlow()

    private var routineId: Long? = null

    /** 每次进入页面（含从动作选择器返回）都重新读一遍，保证编排是最新的。 */
    fun load(id: Long) {
        routineId = id
        viewModelScope.launch { refresh() }
    }

    /** 拖拽结束后按新顺序回写 `position`。 */
    fun reorder(orderedIds: List<Long>) {
        viewModelScope.launch {
            val id = routineId ?: return@launch
            routineRepository.reorderExercises(id, orderedIds)
            refresh()
        }
    }

    fun updateTargets(
        id: Long,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        targetWeight: Double?,
        targetSeconds: Int?,
    ) {
        viewModelScope.launch {
            routineRepository.updateExerciseTargets(
                id = id,
                targetSets = targetSets,
                targetReps = targetReps,
                restSeconds = restSeconds,
                targetWeight = targetWeight,
                targetSeconds = targetSeconds,
            )
            refresh()
        }
    }

    fun removeExercise(id: Long) {
        viewModelScope.launch {
            routineRepository.removeExercise(id)
            refresh()
        }
    }

    private suspend fun refresh() {
        val id = routineId ?: return
        _routineName.value = routineRepository.getById(id)?.name.orEmpty()
        _exercises.value = routineRepository.getExercises(id)
        // 编排（增删动作等）会改变今日计划在组件上的动作数，这里统一兜住。
        widgetManager.updateTodayWidget()
    }
}
