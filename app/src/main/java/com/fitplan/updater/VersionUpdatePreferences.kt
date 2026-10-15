package com.fitplan.updater

import com.fitplan.core.common.preference.Preference
import com.fitplan.core.common.preference.PreferenceStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * 「本次更新」弹窗的展示记录：记住已经给用户看过哪一版的更新说明。
 *
 * 用 `versionCode` 而不是 `versionName` 做比较——Android 判断「装的是不是新版本」也靠它，
 * 每次发版必须递增（见《发布文档》），比版本名可靠。属于内部状态，用 [Preference.appStateKey]，
 * 不进备份。
 *
 * 这个偏好由两处写：首次引导走完时（全新安装不该弹「本次更新」，见 `OnboardingScreenModel`）
 * 与弹窗关掉时（`VersionUpdateScreenModel`）。
 */
@Inject
@SingleIn(AppScope::class)
class VersionUpdatePreferences(preferenceStore: PreferenceStore) {

    /** 已经看过更新说明的 `versionCode`；0 表示从没看过（全新安装，或升级到首个带此功能的版本）。 */
    val seenVersionCode: Preference<Int> = preferenceStore.getInt(Preference.appStateKey(KEY_SEEN_VERSION_CODE), 0)

    private companion object {
        const val KEY_SEEN_VERSION_CODE = "changelog_seen_version_code"
    }
}
