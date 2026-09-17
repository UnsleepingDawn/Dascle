package com.fitplan.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.model.Routine
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
import kotlin.time.Clock

/** 计划列表的一项：计划本身 + 已编排的动作数。 */
data class RoutineListItem(
    val routine: Routine,
    val exerciseCount: Int,
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class PlanScreenModel(
    private val routineRepository: RoutineRepository,
    private val widgetManager: WidgetManager,
) : ViewModel() {

    private val _items = MutableStateFlow<List<RoutineListItem>>(emptyList())
    val items: StateFlow<List<RoutineListItem>> = _items.asStateFlow()

    /** 是否已经完成过一次加载，用来区分「加载中」与「确实没有计划」。 */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _items.value = routineRepository.getAll().map { routine ->
                RoutineListItem(
                    routine = routine,
                    // 本地库数据量很小，逐个查动作数比再加一条聚合查询更省事。
                    exerciseCount = routineRepository.getExercises(routine.id).size,
                )
            }
            _loaded.value = true
        }
    }

    /** 新建后把新方案 id 回调出去，方便调用方直接跳到动作编排页。 */
    fun create(name: String, note: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = routineRepository.insert(name = name, note = note, createdAt = Clock.System.now())
            refresh()
            onCreated(id)
        }
    }

    fun update(id: Long, name: String, note: String) {
        viewModelScope.launch {
            routineRepository.update(id = id, name = name, note = note)
            refresh()
            // 计划名会出现在组件上，改完顺手重画。
            widgetManager.updateTodayWidget()
        }
    }

    /** 动作编排与日程排期都靠外键级联清理，这里删掉计划本身即可。 */
    fun delete(id: Long) {
        viewModelScope.launch {
            routineRepository.deleteById(id)
            refresh()
            widgetManager.updateTodayWidget()
        }
    }
}
