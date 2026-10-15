package com.fitplan.ui.changelog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitplan.app.BuildConfig
import com.fitplan.updater.ReleaseNotesSection
import com.fitplan.updater.UpdateChecker
import com.fitplan.updater.VersionUpdatePreferences
import com.fitplan.updater.parseReleaseNotes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 「本次更新」弹窗当前该显示什么。 */
sealed interface VersionUpdateState {

    /** 不显示：这个版本已经看过，或者还没拿到可展示的内容。 */
    data object Hidden : VersionUpdateState

    /** 有内容可展示。[sections] 已经按重要度排好序。 */
    data class Visible(val version: String, val sections: List<ReleaseNotesSection>) : VersionUpdateState
}

/**
 * 覆盖安装新版本后、第一次打开 App 时的「本次更新」弹窗。
 *
 * 判断依据是偏好里记的 `versionCode` 与当前是否一致，而不是「有没有写过更新说明」：
 * 只有真的换版本才弹，同一天反复冷启动不会再弹。
 *
 * 内容来自该版本 GitHub Release 的正文（见 [UpdateChecker.fetchReleaseForVersion]），
 * 拿到后交给 [parseReleaseNotes] 拆成「新增 / 调整 / 修复」分节并按重要度重排。
 * 取不到就不记状态，下次启动再试——升级前用 App 里的「去下载」走过一趟时缓存里已有说明，
 * 断网也能弹。
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class VersionUpdateScreenModel(
    private val updateChecker: UpdateChecker,
    private val versionUpdatePreferences: VersionUpdatePreferences,
) : ViewModel() {

    /** 当前装的版本，去掉 debug 包的 `-<提交数>` 后缀（tag 上只有 `1.4.4` 这种）。 */
    private val currentVersion = BuildConfig.VERSION_NAME.substringBefore('-')

    private val _state = MutableStateFlow<VersionUpdateState>(VersionUpdateState.Hidden)
    val state: StateFlow<VersionUpdateState> = _state.asStateFlow()

    init {
        // 这个版本已经看过就不必再查了，省一次请求。
        if (versionUpdatePreferences.seenVersionCode.get() != BuildConfig.VERSION_CODE) {
            viewModelScope.launch { load() }
        }
    }

    /** 「知道了」：记下这个版本已经看过，这个版本不再弹。 */
    fun dismiss() {
        versionUpdatePreferences.seenVersionCode.set(BuildConfig.VERSION_CODE)
        _state.value = VersionUpdateState.Hidden
    }

    private suspend fun load() {
        val release = updateChecker.fetchReleaseForVersion(currentVersion)

        // 没取到（断网、仓库里没这个 tag）就什么都不记，下次启动再试，自愈。
        if (release == null) return

        val sections = parseReleaseNotes(release.notes)
        if (sections.isEmpty()) {
            // 这个版本没写更新说明，没什么可展示的；记下来免得每次启动都白查一遍。
            versionUpdatePreferences.seenVersionCode.set(BuildConfig.VERSION_CODE)
            return
        }

        _state.value = VersionUpdateState.Visible(version = currentVersion, sections = sections)
    }
}
