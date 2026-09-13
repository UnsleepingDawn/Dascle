package com.fitplan.ui.plan.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.model.Routine
import com.fitplan.domain.model.ScheduleEntry
import com.fitplan.domain.repository.RoutineRepository
import com.fitplan.domain.repository.ScheduleRepository
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
import kotlinx.datetime.DayOfWeek

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ScheduleScreenModel(
    private val scheduleRepository: ScheduleRepository,
    private val routineRepository: RoutineRepository,
    private val widgetManager: WidgetManager,
) : ViewModel() {

    private val _entries = MutableStateFlow<List<ScheduleEntry>>(emptyList())
    val entries: StateFlow<List<ScheduleEntry>> = _entries.asStateFlow()

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _entries.value = scheduleRepository.getAll()
            _routines.value = routineRepository.getAll()
        }
    }

    /** 把计划挂到「每周 [dayOfWeek]」这一格。 */
    fun addWeekly(routineId: Long, dayOfWeek: DayOfWeek) {
        viewModelScope.launch {
            scheduleRepository.insertWeekly(routineId, dayOfWeek)
            _entries.value = scheduleRepository.getAll()
            widgetManager.updateTodayWidget()
        }
    }

    fun remove(entryId: Long) {
        viewModelScope.launch {
            scheduleRepository.deleteById(entryId)
            _entries.value = scheduleRepository.getAll()
            widgetManager.updateTodayWidget()
        }
    }

    /** 这一天还能排入哪些计划（已经排过的排除掉）。 */
    fun selectableRoutines(dayOfWeek: DayOfWeek): List<Routine> {
        val scheduled = _entries.value
            .filter { it.dayOfWeek == dayOfWeek }
            .map { it.routineId }
            .toSet()
        return _routines.value.filterNot { it.id in scheduled }
    }
}
