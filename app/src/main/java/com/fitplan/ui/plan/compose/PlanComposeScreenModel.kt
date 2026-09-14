package com.fitplan.ui.plan.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.model.Routine
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 编排页里的一格：`Day N` 要练哪个计划，或者干脆休息。
 * 三者互斥——`routineId` 非空是训练，`isRest` 为 true 是休息，都为空则是待填的空白格。
 */
data class ComposeSlot(
    val routineId: Long? = null,
    val routineName: String = "",
    val muscleGroups: List<MuscleGroup> = emptyList(),
    val isRest: Boolean = false,
) {
    val isEmpty: Boolean get() = routineId == null && !isRest
}

/** 末尾的空白格只是「继续往下编排」的入口，不参与循环；这里取实际参与循环的格数。 */
fun composeCycleLength(slots: List<ComposeSlot>): Int = slots.dropLastWhile { it.isEmpty }.size

/**
 * 这次应用会覆盖多少天。[cycles] 与 [until] 只会有一个非空，超过上限时截断，
 * 避免误操作往日历里灌进上万条排期。
 */
fun composePlanDays(
    cycleLength: Int,
    startDate: LocalDate,
    cycles: Int?,
    until: LocalDate?,
): Int {
    if (cycleLength <= 0) return 0
    val raw = when {
        cycles != null -> cycleLength * cycles.coerceAtLeast(1)
        until != null -> (until.toEpochDays() - startDate.toEpochDays() + 1).toInt()
        else -> 0
    }
    return raw.coerceIn(0, MAX_APPLY_DAYS)
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class PlanComposeScreenModel(
    private val scheduleRepository: ScheduleRepository,
    private val routineRepository: RoutineRepository,
    private val widgetManager: WidgetManager,
) : ViewModel() {

    private val _slots = MutableStateFlow(listOf(ComposeSlot()))
    val slots: StateFlow<List<ComposeSlot>> = _slots.asStateFlow()

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    /** 每次进入编排页都从零开始，编排本身只在内存里，退出即丢弃。 */
    fun reset() {
        _slots.value = listOf(ComposeSlot())
    }

    fun load() {
        viewModelScope.launch {
            _routines.value = routineRepository.getAll()
        }
    }

    /** 把第 [index] 格设成练 [routine]，肌群取自该计划已编排的动作。 */
    fun setTraining(index: Int, routine: Routine) {
        if (index !in _slots.value.indices) return
        viewModelScope.launch {
            val slot = ComposeSlot(
                routineId = routine.id,
                routineName = routine.name,
                muscleGroups = routineRepository.getExercises(routine.id)
                    .flatMap { it.muscleGroups }
                    .distinct(),
            )
            _slots.value = normalize(_slots.value.toMutableList().apply { this[index] = slot })
        }
    }

    fun setRest(index: Int) {
        if (index !in _slots.value.indices) return
        val slot = ComposeSlot(isRest = true)
        _slots.value = normalize(_slots.value.toMutableList().apply { this[index] = slot })
    }

    /** 移除这一天：整格抽走，后面的 Day 顺次前移，而不是留一个空洞。 */
    fun clearSlot(index: Int) {
        if (index !in _slots.value.indices) return
        _slots.value = normalize(_slots.value.toMutableList().apply { removeAt(index) })
    }

    /**
     * 把编排按天铺到日历上：`[startDate, startDate + days)` 内先清掉已有的「仅此日」排期与休息日，
     * 再按 [ComposeSlot] 循环逐天写入。整批由数据层放在一个事务里，每周循环排期不受影响。
     *
     * 是挂起函数：调用方要等它写完再离开编排页，否则 ViewModel 被清理会打断写库。
     */
    suspend fun apply(startDate: LocalDate, cycles: Int?, until: LocalDate?) {
        val cycle = _slots.value.dropLastWhile { it.isEmpty }
        val days = composePlanDays(cycle.size, startDate, cycles, until)
        if (days <= 0) return

        val routineDates = mutableMapOf<LocalDate, Long>()
        val restDates = mutableSetOf<LocalDate>()
        var date = startDate
        repeat(days) { index ->
            val slot = cycle[index % cycle.size]
            when {
                slot.isRest -> restDates += date
                slot.routineId != null -> routineDates[date] = slot.routineId
            }
            date = date.plus(1, DateTimeUnit.DAY)
        }

        scheduleRepository.applyComposePlan(
            start = startDate,
            endExclusive = startDate.plus(days, DateTimeUnit.DAY),
            routineDates = routineDates,
            restDates = restDates,
        )
        widgetManager.updateTodayWidget()
    }

    /** 维持不变式：末尾恰好留一个空白格，随时可以继续往下加。 */
    private fun normalize(slots: List<ComposeSlot>): List<ComposeSlot> =
        slots.dropLastWhile { it.isEmpty } + ComposeSlot()
}

/** 单次应用最多铺这么多天，超出就截断。 */
@Suppress("ConstPropertyName")
private const val MAX_APPLY_DAYS = 366
