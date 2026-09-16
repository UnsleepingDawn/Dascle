package com.fitplan.ui.plan.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.interactor.UpdateExerciseProgression
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineItem
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
    private val updateProgress: UpdateExerciseProgression,
    private val widgetManager: WidgetManager,
) : ViewModel() {

    private val _routineName = MutableStateFlow("")
    val routineName: StateFlow<String> = _routineName.asStateFlow()

    /** 顶层编排：单独动作与动作组按共用序号交错排列，组内已含动作。 */
    private val _items = MutableStateFlow<List<RoutineItem>>(emptyList())
    val items: StateFlow<List<RoutineItem>> = _items.asStateFlow()

    private var routineId: Long? = null

    /** 每次进入页面（含从动作选择器返回）都重新读一遍，保证编排是最新的。 */
    fun load(id: Long) {
        routineId = id
        viewModelScope.launch { refresh() }
    }

    /** 新增一个空的动作组，放在计划末尾；之后往里加动作、设「做其中 x 个」。 */
    fun addGroup() {
        viewModelScope.launch {
            val id = routineId ?: return@launch
            routineRepository.addGroup(id)
            refresh()
        }
    }

    /** 设「做其中 x 个」，取值范围由 repository 按组内动作数收敛。 */
    fun updateGroupMaxPicks(groupId: Long, maxPicks: Int) {
        viewModelScope.launch {
            routineRepository.updateGroupMaxPicks(groupId, maxPicks)
            refresh()
        }
    }

    fun removeGroup(groupId: Long) {
        viewModelScope.launch {
            routineRepository.removeGroup(groupId)
            refresh()
        }
    }

    /** 拖拽结束后按新顺序回写 `position`（动作与动作组共用一套序号）。 */
    fun reorder(orderedItems: List<RoutineItem>) {
        viewModelScope.launch {
            val id = routineId ?: return@launch
            routineRepository.reorderItems(id, orderedItems)
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
            val previous = findExercise(id)
            routineRepository.updateExerciseTargets(
                id = id,
                targetSets = targetSets,
                targetReps = targetReps,
                restSeconds = restSeconds,
                targetWeight = targetWeight,
                targetSeconds = targetSeconds,
            )
            // 用户主动把目标改得更难时，解除这个动作渐进提示的暂缓 / 休眠
            // （辅助类动作是调低辅助重量才更难）。
            previous?.let {
                updateProgress.onManualWeightChanged(
                    exerciseId = it.exerciseId,
                    previousWeight = it.targetWeight,
                    newWeight = targetWeight,
                    loadMode = it.loadMode,
                )
            }
            refresh()
        }
    }

    fun removeExercise(id: Long) {
        viewModelScope.launch {
            routineRepository.removeExercise(id)
            refresh()
        }
    }

    private fun findExercise(id: Long): RoutineExercise? = _items.value
        .flatMap { it.exercises }
        .firstOrNull { it.id == id }

    private suspend fun refresh() {
        val id = routineId ?: return
        _routineName.value = routineRepository.getById(id)?.name.orEmpty()
        _items.value = routineRepository.getItems(id)
        // 编排（增删动作等）会改变今日计划在组件上的动作数，这里统一兜住。
        widgetManager.updateTodayWidget()
    }
}

/** 顶层项在列表 / 拖拽里的唯一 key，动作与动作组会重 id，必须带前缀。 */
internal val RoutineItem.listKey: String
    get() = when (this) {
        is RoutineItem.Exercise -> "e${value.id}"
        is RoutineItem.Group -> "g${value.id}"
    }
