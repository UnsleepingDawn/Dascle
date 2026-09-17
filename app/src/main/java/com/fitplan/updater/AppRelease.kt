package com.fitplan.updater

import kotlinx.serialization.Serializable

/**
 * 一个 GitHub Release 的摘要，只保留界面需要的字段。
 *
 * [version] 已经去掉 tag 的 `v` 前缀；[pageUrl] 是浏览器打开的地址（用镜像源成功时会带上镜像前缀）。
 */
@Serializable
data class AppRelease(
    val version: String,
    val pageUrl: String,
    val notes: String = "",
    val apkName: String? = null,
)

/** 检查更新失败的原因，用来决定给用户看哪种文案。 */
enum class FailureReason {
    /** 连不上（断网、超时、被墙）。 */
    NETWORK,

    /** 服务器返回了非成功状态码。 */
    SERVER,

    /** GitHub API 触发限流（403 / 429）。 */
    RATE_LIMITED,

    /**
     * GitHub 上找不到可用的 Release：仓库里一条都没有，或者全是草稿。
     *
     * 与 [SERVER] 区分开，因为这不是「服务器出错」，用户该去仓库发一个版本，重试没用。
     */
    NO_RELEASE,
}

/** 一次检查的结果。 */
sealed interface UpdateCheckResult {

    /** 发现比当前更新的版本。 */
    data class Available(val release: AppRelease) : UpdateCheckResult

    /** 已经是最新版本。 */
    data object UpToDate : UpdateCheckResult

    /** 没真的去查：关掉了自动检查，或者刚查过没多久。 */
    data object Skipped : UpdateCheckResult

    /** 查了但失败。 */
    data class Failed(val reason: FailureReason) : UpdateCheckResult
}
