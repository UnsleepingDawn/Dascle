package com.fitplan.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.app.data.DataRevision
import com.fitplan.domain.interactor.GetScheduledRoutinesForDate
import com.fitplan.domain.interactor.ScheduledRoutine
import com.fitplan.domain.model.WorkoutSession
import com.fitplan.domain.repository.WorkoutRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class TodayScreenModel(
    private val getScheduledRoutinesForDate: GetScheduledRoutinesForDate,
    private val workoutRepository: WorkoutRepository,
    dataRevision: DataRevision,
) : ViewModel() {

    init {
        // 启动时的漏练弹窗可能在今日页取过数之后才改排期（顺延 / 跳过），收到信号就重新加载。
        viewModelScope.launch {
            dataRevision.revision.drop(1).collect { refresh() }
        }
    }

    private val _date = MutableStateFlow(today())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _routines = MutableStateFlow<List<ScheduledRoutine>>(emptyList())
    val routines: StateFlow<List<ScheduledRoutine>> = _routines.asStateFlow()

    /** 上一次没练完就退出的训练，用于把「开始训练」换成「继续训练」。 */
    private val _unfinished = MutableStateFlow<WorkoutSession?>(null)
    val unfinished: StateFlow<WorkoutSession?> = _unfinished.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val date = today()
            _date.value = date
            _routines.value = getScheduledRoutinesForDate(date)
            _unfinished.value = workoutRepository.getUnfinishedSessions().maxByOrNull { it.startedAt }
            _loaded.value = true
        }
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
