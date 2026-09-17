package com.fitplan.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.app.BuildConfig
import com.fitplan.updater.AppRelease
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

/**
 * 启动时的更新提示：静默查一次，有新版就弹窗。
 *
 * 和漏练弹窗一样挂在 `HomeScreen` 上，冷启动进组合时跑一次。查完不论结果如何，
 * 只把「有比当前新、且还没提示过」的版本交给界面——同一天内不会反复弹。
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class UpdatePromptScreenModel(
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    private val _dialog = MutableStateFlow<AppRelease?>(null)
    val dialog: StateFlow<AppRelease?> = _dialog.asStateFlow()

    init {
        viewModelScope.launch {
            // 先看看缓存里有没有上次查到、还没提示过的新版本，有就立刻弹，不用等网络。
            _dialog.value = updateChecker.pendingPrompt(BuildConfig.VERSION_NAME)
            val result = updateChecker.check(BuildConfig.VERSION_NAME, force = false)
            if (result is UpdateCheckResult.Available) {
                _dialog.value = updateChecker.pendingPrompt(BuildConfig.VERSION_NAME)
            }
        }
    }

    /** 「去下载」或「稍后再说」都调它：记下这个版本已提示过，不再重复弹。 */
    fun dismiss() {
        _dialog.value?.let { updateChecker.markNotified(it.version) }
        _dialog.value = null
    }
}
