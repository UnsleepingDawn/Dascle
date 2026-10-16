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

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class PlanScreenModel(
    private val routineRepository: RoutineRepository,
    private val widgetManager: WidgetManager,
) : ViewModel() {

    private val _items = MutableStateFlow<List<Routine>>(emptyList())
    val items: StateFlow<List<Routine>> = _items.asStateFlow()

    /** 是否已经完成过一次加载，用来区分「加载中」与「确实没有计划」。 */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _items.value = routineRepository.getPermanent()
            _loaded.value = true
        }
    }

    /** 新建后把新计划 id 回调出去，方便调用方直接跳到动作编排页。 */
    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = routineRepository.insert(name = name, createdAt = Clock.System.now())
            refresh()
            onCreated(id)
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
