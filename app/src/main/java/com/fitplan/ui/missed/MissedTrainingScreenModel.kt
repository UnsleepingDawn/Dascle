package com.fitplan.ui.missed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.app.data.DataRevision
import com.fitplan.core.common.preference.Preference
import com.fitplan.core.common.preference.PreferenceStore
import com.fitplan.domain.interactor.GetMissedTraining
import com.fitplan.domain.interactor.MissedTraining
import com.fitplan.domain.interactor.PostponeMissedTraining
import com.fitplan.domain.interactor.SkipMissedTraining
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** 漏练弹窗当前该显示什么。 */
sealed interface MissedDialogState {

    data object Hidden : MissedDialogState

    /** 第一步：这段时间到底练了没有。 */
    data class Confirm(val missed: MissedTraining) : MissedDialogState

    /** 第二步：没练的话，顺延还是跳过。 */
    data class Handle(val missed: MissedTraining) : MissedDialogState
}

/**
 * 打开 App 时检查有没有「安排了训练却没练」的日子，有就先弹窗问怎么处理。
 *
 * 用户任一分支处理完都会把「已处理到哪一天」记下来，所以同一天不会反复问；
 * 处理完再 [DataRevision.bump]，让已经取过数的今日页与日历页重新加载。
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class MissedTrainingScreenModel(
    private val getMissedTraining: GetMissedTraining,
    private val postponeMissedTraining: PostponeMissedTraining,
    private val skipMissedTraining: SkipMissedTraining,
    private val dataRevision: DataRevision,
    private val widgetManager: WidgetManager,
    preferenceStore: PreferenceStore,
) : ViewModel() {

    /** 已处理到哪一天（epochDays）；0 表示从未处理过。属于内部状态，不进备份。 */
    private val handledUntil = preferenceStore.getLong(Preference.appStateKey(KEY_HANDLED_UNTIL), 0L)

    private val _dialog = MutableStateFlow<MissedDialogState>(MissedDialogState.Hidden)
    val dialog: StateFlow<MissedDialogState> = _dialog.asStateFlow()

    /** 每次回到前台都查一遍；弹窗正开着就不打扰用户。 */
    fun check() {
        if (_dialog.value != MissedDialogState.Hidden) return
        viewModelScope.launch {
            val until = handledUntil.get().takeIf { it > 0L }?.let(LocalDate::fromEpochDays)
            val missed = getMissedTraining(until) ?: return@launch
            if (_dialog.value == MissedDialogState.Hidden) {
                _dialog.value = MissedDialogState.Confirm(missed)
            }
        }
    }

    /** 「健身了，没写上去」：只是别再来问了，排期一动不动。 */
    fun confirmTrained() = finish()

    /** 点弹窗外 / 返回键：等同「健身了，没写上去」，避免误触改掉日历。 */
    fun dismiss() = finish()

    fun askHowToHandle(missed: MissedTraining) {
        _dialog.value = MissedDialogState.Handle(missed)
    }

    fun postpone(missed: MissedTraining) {
        viewModelScope.launch {
            postponeMissedTraining(missed)
            finish()
        }
    }

    fun skip(missed: MissedTraining) {
        viewModelScope.launch {
            skipMissedTraining(missed)
            finish()
        }
    }

    private fun finish() {
        viewModelScope.launch {
            handledUntil.set(today().toEpochDays())
            _dialog.value = MissedDialogState.Hidden
            dataRevision.bump()
            widgetManager.updateTodayWidget()
        }
    }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private companion object {
        const val KEY_HANDLED_UNTIL = "missed_training_handled_until"
    }
}
