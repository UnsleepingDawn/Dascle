package com.fitplan.ui.plan.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.core.common.preference.Preference
import com.fitplan.core.common.preference.PreferenceStore
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 编排页里的一格：`Day N` 要练哪个计划，或者干脆休息。
 * 三者互斥——`routineId` 非空是训练，`isRest` 为 true 是休息，都为空则是待填的空白格。
 */
data class ComposeSlot(
    /** 这一格的稳定标识，只用来给拖动排序认人 / 当列表 key，与「第几天」无关。 */
    val id: Long,
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
    private val json: Json,
    preferenceStore: PreferenceStore,
) : ViewModel() {

    /**
     * 编排的暂存：切走子页乃至杀掉进程再回来，都还能接着上次那套编排继续改。
     * 属于内部状态、不进备份，所以键放在 `__APP_STATE_` 前缀下。
     */
    private val draft = preferenceStore.getString(Preference.appStateKey(KEY_DRAFT))

    /** 空格子的自增标识；只增不减，保证拖动排序时每一格都有稳定且唯一的 key。 */
    private var nextSlotId = 0L

    private val _slots = MutableStateFlow(listOf(newEmptySlot()))
    val slots: StateFlow<List<ComposeSlot>> = _slots.asStateFlow()

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    /**
     * 把暂存的编排读回来。返回 true 表示有内容可以接着编辑，界面该弹一次窗问用户；
     * 没有暂存（或暂存已经读不出来）时返回 false，并把编排清成「只有一个空白格」。
     */
    fun restoreDraft(): Boolean {
        val text = draft.get()
        val restored = if (text.isBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<ComposeDraft>>(text) }
                .getOrDefault(emptyList())
                .map { it.toSlot() }
        }
        if (restored.isEmpty()) {
            clearDraft()
            resetToEmpty()
            return false
        }
        _slots.value = normalize(restored)
        return true
    }

    /** 用户选了「不要了」：丢掉暂存，回到只有一个空白格的初始状态。 */
    fun discardDraft() {
        clearDraft()
        resetToEmpty()
    }

    fun load() {
        viewModelScope.launch {
            _routines.value = routineRepository.getPermanent()
        }
    }

    /** 把第 [index] 格设成练 [routine]，肌群取自该计划已编排的动作。 */
    fun setTraining(index: Int, routine: Routine) {
        val current = _slots.value.getOrNull(index) ?: return
        viewModelScope.launch {
            val slot = ComposeSlot(
                id = current.id,
                routineId = routine.id,
                routineName = routine.name,
                muscleGroups = routineRepository.getExercises(routine.id)
                    .flatMap { it.muscleGroups }
                    .distinct(),
            )
            update(_slots.value.toMutableList().apply { this[index] = slot })
        }
    }

    fun setRest(index: Int) {
        val current = _slots.value.getOrNull(index) ?: return
        val slot = ComposeSlot(id = current.id, isRest = true)
        update(_slots.value.toMutableList().apply { this[index] = slot })
    }

    /** 移除这一天：整格抽走，后面的 Day 顺次前移，而不是留一个空洞。 */
    fun clearSlot(index: Int) {
        if (index !in _slots.value.indices) return
        update(_slots.value.toMutableList().apply { removeAt(index) })
    }

    /**
     * 把第 [from] 格拖到第 [to] 格。末尾那个「加一天」的空白格不参与排序，
     * 始终留在最后，所以能落到的位置最多是倒数第二格。
     */
    fun reorder(from: Int, to: Int) {
        val slots = _slots.value
        if (slots.size <= 2) return
        val lastRealIndex = slots.size - 2
        val fromIndex = from.coerceIn(0, lastRealIndex)
        val toIndex = to.coerceIn(0, lastRealIndex)
        if (fromIndex == toIndex) return
        update(slots.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
    }

    /**
     * 把编排按天铺到日历上：`[startDate, startDate + days)` 内先清掉已有的排期，再按 [ComposeSlot]
     * 循环逐天写入（休息格不写排期，那天自然就是休息日）。整批由数据层放在一个事务里。
     *
     * 是挂起函数：调用方要等它写完再离开编排页，否则 ViewModel 被清理会打断写库。
     */
    suspend fun apply(startDate: LocalDate, cycles: Int?, until: LocalDate?) {
        val cycle = _slots.value.dropLastWhile { it.isEmpty }
        val days = composePlanDays(cycle.size, startDate, cycles, until)
        if (days <= 0) return

        val routineDates = mutableMapOf<LocalDate, Long>()
        var date = startDate
        repeat(days) { index ->
            val slot = cycle[index % cycle.size]
            // 休息格不写任何排期：没被写到的日子自然就是休息日。
            slot.routineId?.let { routineDates[date] = it }
            date = date.plus(1, DateTimeUnit.DAY)
        }

        scheduleRepository.applyComposePlan(
            start = startDate,
            endExclusive = startDate.plus(days, DateTimeUnit.DAY),
            routineDates = routineDates,
        )
        widgetManager.updateTodayWidget()
        // 已经铺到日历上了，暂存就没有意义了：清掉，免得下次进来又问一遍。
        clearDraft()
    }

    /** 编排有改动就顺手写一次暂存，界面只需要一个 [update] 入口。 */
    private fun update(slots: List<ComposeSlot>) {
        val next = normalize(slots)
        _slots.value = next
        val realSlots = next.dropLastWhile { it.isEmpty }
        if (realSlots.isEmpty()) {
            draft.delete()
        } else {
            draft.set(json.encodeToString(realSlots.map { it.toDraft() }))
        }
    }

    /** 丢掉暂存；编排留在原地不动，界面自己决定要不要 [resetToEmpty]。 */
    private fun clearDraft() = draft.delete()

    /** 编排清成「只有一个空白格」，等着用户重新排。 */
    private fun resetToEmpty() {
        _slots.value = listOf(newEmptySlot())
    }

    /** 维持不变式：末尾恰好留一个空白格，随时可以继续往下加。 */
    private fun normalize(slots: List<ComposeSlot>): List<ComposeSlot> {
        val realSlots = slots.dropLastWhile { it.isEmpty }
        // 末尾空格的 id 尽量沿用，免得只是改动中间某格时，「加一天」那张卡被当成新条目重建。
        val tail = slots.lastOrNull { it.isEmpty } ?: newEmptySlot()
        return realSlots + tail
    }

    private fun newEmptySlot(): ComposeSlot = ComposeSlot(id = nextSlotId++)

    private fun ComposeDraft.toSlot(): ComposeSlot = ComposeSlot(
        id = nextSlotId++,
        routineId = routineId,
        routineName = routineName,
        muscleGroups = muscleGroups.mapNotNull(MuscleGroup::fromValue),
        isRest = isRest,
    )

    private fun ComposeSlot.toDraft(): ComposeDraft = ComposeDraft(
        routineId = routineId,
        routineName = routineName,
        muscleGroups = muscleGroups.map(MuscleGroup::name),
        isRest = isRest,
    )

    private companion object {
        /** 暂存键；改键名等于放弃所有人已有的暂存。 */
        const val KEY_DRAFT = "plan_compose_draft"
    }
}

/**
 * 暂存用的扁平结构：只存能重建编排的最小信息。
 *
 * 肌群按枚举名存，读回来时认不出的名字直接丢掉——以后改动 [MuscleGroup] 也不会让老暂存整条读不出来。
 */
@Serializable
private data class ComposeDraft(
    val routineId: Long? = null,
    val routineName: String = "",
    val muscleGroups: List<String> = emptyList(),
    val isRest: Boolean = false,
)

/** 单次应用最多铺这么多天，超出就截断。 */
@Suppress("ConstPropertyName")
private const val MAX_APPLY_DAYS = 366
