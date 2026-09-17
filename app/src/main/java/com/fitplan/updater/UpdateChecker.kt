package com.fitplan.updater

import android.util.Log
import com.fitplan.core.common.preference.Preference
import com.fitplan.core.common.preference.PreferenceStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * 「检查新版本」的唯一入口：负责请求、节流、把结果缓存进偏好，界面层只管调它。
 *
 * 节流规则（[check] 的 `force = false` 时生效）：
 * - 关掉了自动检查 → [UpdateCheckResult.Skipped]；
 * - 距上次**成功**检查不足一天 → [UpdateCheckResult.Skipped]。
 *
 * 结果缓存在偏好里，所以「昨天查到有新版本但没点进去」的情况下，今天打开 App 仍会提示，
 * 靠 [pendingPrompt] 判断，同时用 [markNotified] 保证同一个版本只弹一次。
 */
@Inject
@SingleIn(AppScope::class)
class UpdateChecker(
    preferenceStore: PreferenceStore,
    private val json: Json,
) {

    /** 启动时自动检查的开关。 */
    val autoCheck: Preference<Boolean> = preferenceStore.getBoolean(KEY_AUTO_CHECK, true)

    /** 镜像前缀，留空表示只直连 GitHub。 */
    val mirrorPrefix: Preference<String> = preferenceStore.getString(KEY_MIRROR_PREFIX, DEFAULT_MIRROR_PREFIX)

    private val lastCheckAt = preferenceStore.getLong(Preference.appStateKey(KEY_LAST_CHECK_AT))

    private val cachedRelease = preferenceStore.getString(Preference.appStateKey(KEY_CACHED_RELEASE))

    private val notifiedVersion = preferenceStore.getString(Preference.appStateKey(KEY_NOTIFIED_VERSION))

    private val api = GitHubReleasesApi(json)

    /**
     * 查一次最新版本。
     *
     * @param force 手动检查传 true，绕过开关与一天的节流。
     */
    suspend fun check(currentVersion: String, force: Boolean): UpdateCheckResult {
        if (!force) {
            if (!autoCheck.get()) return UpdateCheckResult.Skipped
            val elapsed = Clock.System.now().toEpochMilliseconds() - lastCheckAt.get()
            if (elapsed < CHECK_INTERVAL_MS) return UpdateCheckResult.Skipped
        }

        val result = api.fetchLatest(
            mirrorPrefix = mirrorPrefix.get().trim(),
            userAgent = "$USER_AGENT_PREFIX/$currentVersion",
        )

        return when (result) {
            is ApiResult.Failure -> {
                // 失败不改 lastCheckAt，下次启动会再试；缓存里已有的结果先留着。
                Log.d(TAG, "检查更新失败：${result.reason}")
                UpdateCheckResult.Failed(result.reason)
            }

            is ApiResult.Success -> {
                lastCheckAt.set(Clock.System.now().toEpochMilliseconds())
                val release = result.release
                if (isNewer(release.version, currentVersion)) {
                    cachedRelease.set(json.encodeToString(release))
                    UpdateCheckResult.Available(release)
                } else {
                    cachedRelease.delete()
                    UpdateCheckResult.UpToDate
                }
            }
        }
    }

    /** 缓存里有没有「比当前新」的版本，不管有没有提示过。 */
    fun availableRelease(currentVersion: String): AppRelease? =
        cachedReleaseOrNull()?.takeIf { isNewer(it.version, currentVersion) }

    /** 缓存里有没有「比当前新、而且还没提示过」的版本，有就返回它。 */
    fun pendingPrompt(currentVersion: String): AppRelease? =
        availableRelease(currentVersion)?.takeIf { it.version != notifiedVersion.get() }

    /** 连同 [pendingPrompt] 缓存的那个版本一起读出来，不判断是否已提示。 */
    fun cachedReleaseOrNull(): AppRelease? {
        val raw = cachedRelease.get()
        if (raw.isBlank()) return null
        return try {
            json.decodeFromString<AppRelease>(raw)
        } catch (e: Exception) {
            Log.d(TAG, "缓存的新版本信息读不出来，忽略", e)
            null
        }
    }

    /** 弹过更新提示之后调用，同一个版本不再重复弹。 */
    fun markNotified(version: String) {
        notifiedVersion.set(version)
    }

    private companion object {
        const val TAG = "FitPlan"
        const val USER_AGENT_PREFIX = "Dascle"
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

        const val KEY_AUTO_CHECK = "update_auto_check"
        const val KEY_MIRROR_PREFIX = "update_mirror_prefix"
        const val KEY_LAST_CHECK_AT = "update_last_check_at"
        const val KEY_CACHED_RELEASE = "update_available"
        const val KEY_NOTIFIED_VERSION = "update_notified_version"

        /** 默认镜像：GitHub 直连不通时用（`gh-proxy` 的公共实例，失效时改这一处）。 */
        const val DEFAULT_MIRROR_PREFIX = "https://gh-proxy.com/"
    }
}
