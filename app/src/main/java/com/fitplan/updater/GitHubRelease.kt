package com.fitplan.updater

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub `GET /releases` 返回的数组里的一项，只解析用得到的字段，
 * 其余交给 `Json { ignoreUnknownKeys = true }` 丢掉。
 */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
internal data class GitHubReleaseAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
)

/**
 * 从 Release 列表里挑要提示给用户的那一条。
 *
 * 优先「非草稿且非预发布」；没有这样的版本时退回「非草稿」里最新的一条——
 * 本仓库历史上把正式版本都标成了「预发布」，这条兜底能保证这种情况下照样提示更新。
 * 列表本身按创建时间倒序，所以第一个命中的就是最新的。草稿一律不要。
 */
internal fun List<GitHubRelease>.pickLatest(): GitHubRelease? =
    firstOrNull { !it.draft && !it.prerelease } ?: firstOrNull { !it.draft }

/**
 * 转成给界面用的模型。
 *
 * [urlPrefix] 是本次请求实际用的镜像前缀（直连时为空）：走镜像成功时页面地址也要带上它，
 * 否则网络不通的情况下打开下载页照样是白的。
 */
internal fun GitHubRelease.toAppRelease(urlPrefix: String): AppRelease = AppRelease(
    version = tagName.removePrefix("v").removePrefix("V").trim(),
    pageUrl = urlPrefix + htmlUrl,
    notes = body.trim(),
    apkName = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.name,
)
