package com.fitplan.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.core.common.preference.Preference
import com.fitplan.core.common.preference.PreferenceStore
import com.fitplan.domain.interactor.GetBodyMetricReminder
import com.fitplan.domain.interactor.GetBodyStats
import com.fitplan.domain.interactor.GetWorkoutStats
import com.fitplan.domain.model.BodyMetricReminder
import com.fitplan.domain.model.BodyStats
import com.fitplan.domain.model.StatsRange
import com.fitplan.domain.model.WorkoutStats
import com.fitplan.domain.repository.BodyMetricRepository
import com.fitplan.ui.profile.BodyMetricField
import com.fitplan.ui.profile.formatMetric
import com.fitplan.ui.profile.parseMetric
import com.fitplan.ui.profile.today
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

/** 统计页的两个标签：锻炼数据与身体数据。 */
enum class StatsPage {
    WORKOUT,
    BODY,
}

/** 记录今日身体数据的输入框状态：[field] 决定记哪一项，[prefill] 是打开时的预填值。 */
data class BodyInputState(
    val field: BodyMetricField,
    val prefill: String,
)

/**
 * 统计页：两个标签共用同一套时间区间（切标签时区间不变），各自缓存自己的取数结果。
 *
 * 进入页面时顺带判断要不要提醒补记身体数据，每项最多一周提醒一次，当天点过「以后再说」就不再弹。
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class StatsScreenModel(
    private val getWorkoutStats: GetWorkoutStats,
    private val getBodyStats: GetBodyStats,
    private val getBodyMetricReminder: GetBodyMetricReminder,
    private val bodyMetricRepository: BodyMetricRepository,
    preferenceStore: PreferenceStore,
) : ViewModel() {

    /** 记住「哪天已经提醒过」，同一项当天不再打扰。属于内部状态，不进备份。 */
    private val reminderDismissedOn =
        preferenceStore.getLong(Preference.appStateKey(KEY_REMINDER_DISMISSED_ON))

    private val _page = MutableStateFlow(StatsPage.WORKOUT)
    val page: StateFlow<StatsPage> = _page.asStateFlow()

    private val _range = MutableStateFlow(RANGES.first())
    val range: StateFlow<StatsRange> = _range.asStateFlow()

    private val _axisMode = MutableStateFlow(ProgressAxisMode.INDEX)
    val axisMode: StateFlow<ProgressAxisMode> = _axisMode.asStateFlow()

    private val _stats = MutableStateFlow<WorkoutStats?>(null)
    val stats: StateFlow<WorkoutStats?> = _stats.asStateFlow()

    private val _bodyStats = MutableStateFlow<BodyStats?>(null)
    val bodyStats: StateFlow<BodyStats?> = _bodyStats.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** 重量进步图上选中的动作，默认取区间内数据最多的那个。 */
    private val _selectedExerciseId = MutableStateFlow<Long?>(null)
    val selectedExerciseId: StateFlow<Long?> = _selectedExerciseId.asStateFlow()

    private val _bodyInput = MutableStateFlow<BodyInputState?>(null)
    val bodyInput: StateFlow<BodyInputState?> = _bodyInput.asStateFlow()

    private val _reminder = MutableStateFlow<BodyMetricReminder?>(null)
    val reminder: StateFlow<BodyMetricReminder?> = _reminder.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            if (_page.value == StatsPage.WORKOUT) {
                val stats = getWorkoutStats(_range.value)
                _stats.value = stats
                if (stats.exerciseProgress.none { it.exerciseId == _selectedExerciseId.value }) {
                    _selectedExerciseId.value = stats.exerciseProgress.firstOrNull()?.exerciseId
                }
            } else {
                _bodyStats.value = getBodyStats(_range.value)
            }
            _loading.value = false
            refreshReminder()
        }
    }

    fun selectPage(page: StatsPage) {
        if (_page.value == page) return
        _page.value = page
        // 两个标签各自缓存结果，但区间是共用的：切回来时区间可能已经变了，一律重新取一次。
        refresh()
    }

    fun selectRange(range: StatsRange) {
        if (_range.value == range) return
        _range.value = range
        _loading.value = true
        refresh()
    }

    fun selectAxisMode(mode: ProgressAxisMode) {
        _axisMode.value = mode
    }

    fun selectExercise(exerciseId: Long) {
        _selectedExerciseId.value = exerciseId
    }

    /** 打开记录对话框：预填最近一次的值（今天记过就是今天的），没有记录则留空。 */
    fun openBodyInput(field: BodyMetricField) {
        _bodyInput.value = BodyInputState(field = field, prefill = latestValue(field)?.let(::formatMetric).orEmpty())
    }

    fun dismissBodyInput() {
        _bodyInput.value = null
    }

    fun saveBodyInput(text: String) {
        val input = _bodyInput.value ?: return
        val value = parseMetric(input.field, text) ?: return
        viewModelScope.launch {
            when (input.field) {
                BodyMetricField.WEIGHT -> bodyMetricRepository.recordWeight(today(), value)
                BodyMetricField.BODY_FAT -> bodyMetricRepository.recordBodyFat(today(), value)
            }
            _bodyInput.value = null
            refresh()
        }
    }

    /** 今天已经记过的值（如果有），用于记录对话框里的覆盖提示。 */
    fun todayValue(field: BodyMetricField): Double? {
        val latest = _bodyStats.value ?: return null
        val recorded = when (field) {
            BodyMetricField.WEIGHT -> latest.latestWeight
            BodyMetricField.BODY_FAT -> latest.latestBodyFat
        } ?: return null
        if (recorded.date != today()) return null
        return when (field) {
            BodyMetricField.WEIGHT -> recorded.weight
            BodyMetricField.BODY_FAT -> recorded.bodyFat
        }
    }

    /** 「去记录」：切到身体数据标签并直接打开缺的那一项。 */
    fun recordFromReminder() {
        val reminder = _reminder.value ?: return
        val field = if (reminder.needWeight) BodyMetricField.WEIGHT else BodyMetricField.BODY_FAT
        selectPage(StatsPage.BODY)
        dismissReminder()
        openBodyInput(field)
    }

    /** 「以后再说」：今天不再提醒，明天进入统计页会重新判断。 */
    fun dismissReminder() {
        reminderDismissedOn.set(today().toEpochDays())
        _reminder.value = null
    }

    private suspend fun refreshReminder() {
        if (reminderDismissedOn.get() == today().toEpochDays()) {
            _reminder.value = null
            return
        }
        _reminder.value = getBodyMetricReminder()
    }

    private fun latestValue(field: BodyMetricField): Double? {
        val latest = _bodyStats.value ?: return null
        return when (field) {
            BodyMetricField.WEIGHT -> latest.latestWeight?.weight
            BodyMetricField.BODY_FAT -> latest.latestBodyFat?.bodyFat
        }
    }

    companion object {
        /** 区间切换的选项，顺序就是选择器里 chip 的排列顺序。 */
        val RANGES = StatsRange.entries

        private const val KEY_REMINDER_DISMISSED_ON = "body_metric_reminder_dismissed_on"
    }
}
