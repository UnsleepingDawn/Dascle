package com.fitplan.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.app.BuildConfig
import com.fitplan.updater.AppRelease
import com.fitplan.updater.FailureReason
import com.fitplan.updater.UpdateCheckResult
import com.fitplan.updater.UpdateChecker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 「关于」页手动检查更新的状态。 */
sealed interface ManualCheckState {

    /** 还没查过，右侧不显示状态文案。 */
    data object Idle : ManualCheckState

    data object Checking : ManualCheckState

    data class UpToDate(val currentVersion: String) : ManualCheckState

    data class Available(val release: AppRelease) : ManualCheckState

    data class Failed(val reason: FailureReason) : ManualCheckState
}

/**
 * 「关于」页的更新检查：手动点一次，强制联网绕过节流。
 *
 * 进页面时如果缓存里有比当前新的版本就直接显示出来，用户不用再点一次。
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class AboutScreenModel(
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    private val _state = MutableStateFlow<ManualCheckState>(initialState())
    val state: StateFlow<ManualCheckState> = _state.asStateFlow()

    fun check() {
        if (_state.value == ManualCheckState.Checking) return
        _state.value = ManualCheckState.Checking
        viewModelScope.launch {
            _state.value = when (val result = updateChecker.check(BuildConfig.VERSION_NAME, force = true)) {
                is UpdateCheckResult.Available -> ManualCheckState.Available(result.release)
                UpdateCheckResult.UpToDate -> ManualCheckState.UpToDate(BuildConfig.VERSION_NAME)
                is UpdateCheckResult.Failed -> ManualCheckState.Failed(result.reason)
                // force = true 不会被跳过，真出现了就当没查过。
                UpdateCheckResult.Skipped -> ManualCheckState.Idle
            }
        }
    }

    /** 用户点了「发现新版本」之后，这个版本不再弹启动提示。 */
    fun markNotified(release: AppRelease) {
        updateChecker.markNotified(release.version)
    }

    private fun initialState(): ManualCheckState =
        updateChecker.availableRelease(BuildConfig.VERSION_NAME)
            ?.let { ManualCheckState.Available(it) }
            ?: ManualCheckState.Idle
}
