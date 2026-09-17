package com.fitplan.updater

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 向 GitHub 查最新 Release。
 *
 * 只用 JDK 自带的 [HttpURLConnection]，不引入网络库。请求顺序是「直连 api.github.com →
 * 失败且配了镜像前缀 → 走镜像」，两条都不通才把失败原因报上去。
 *
 * 用 `GET /releases` 列表接口而不是更省事的 `/releases/latest`：后者只返回「最新的非草稿、
 * 非预发布」的那一条，项目只要把所有版本都标成「预发布」（本仓库的 `v1.0.0` ~ `v1.1.1` 就是），
 * 它就恒返回 404，网络完全正常也查不到新版本。列表接口稳定返回非草稿条目，由 [pickLatest] 自己挑。
 *
 * 注意 GitHub 要求带上 `User-Agent`，缺了会直接返回 403。
 */
internal class GitHubReleasesApi(private val json: Json) {

    /**
     * 查最新 Release。
     *
     * @param mirrorPrefix 非空时会作为镜像前缀重试一次（形如 `https://gh-proxy.com/`）。
     */
    suspend fun fetchLatest(mirrorPrefix: String, userAgent: String): ApiResult = withContext(Dispatchers.IO) {
        val direct = request(endpoint = RELEASES_URL, userAgent = userAgent, urlPrefix = "")
        if (direct is ApiResult.Success || mirrorPrefix.isBlank()) return@withContext direct

        Log.d(TAG, "直连 GitHub 失败（$direct），改用镜像前缀 $mirrorPrefix")
        request(endpoint = RELEASES_URL, userAgent = userAgent, urlPrefix = mirrorPrefix)
    }

    private fun request(endpoint: String, userAgent: String, urlPrefix: String): ApiResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlPrefix + endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", ACCEPT_HEADER)
                setRequestProperty("X-GitHub-Api-Version", API_VERSION)
                setRequestProperty("User-Agent", userAgent)
            }

            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS ->
                    ApiResult.Failure(FailureReason.RATE_LIMITED)

                // 仓库不存在或读不到 Release，重试也没用。
                HttpURLConnection.HTTP_NOT_FOUND -> ApiResult.Failure(FailureReason.NO_RELEASE)

                200 -> {
                    val releases = json.decodeFromString<List<GitHubRelease>>(
                        connection.inputStream.bufferedReader().use { it.readText() },
                    )
                    val picked = releases.pickLatest()
                    if (picked == null) {
                        Log.d(TAG, "GitHub 上没有可用的 Release（共 ${releases.size} 条）")
                        ApiResult.Failure(FailureReason.NO_RELEASE)
                    } else {
                        ApiResult.Success(picked.toAppRelease(urlPrefix))
                    }
                }

                else -> {
                    Log.d(TAG, "GitHub 返回 $code")
                    ApiResult.Failure(FailureReason.SERVER)
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, "请求 GitHub 失败", e)
            ApiResult.Failure(FailureReason.NETWORK)
        } catch (e: SerializationException) {
            // 响应不是预期的 JSON（例如撞上了镜像的错误页）。
            Log.d(TAG, "GitHub 响应解析失败", e)
            ApiResult.Failure(FailureReason.SERVER)
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val TAG = "FitPlan"

        /**
         * 用列表接口（`/releases`）而不是 `/releases/latest`，原因见类注释。
         * `per_page` 压到 5：只拿最近几条就够挑出最新的，顺带少传点更新说明。
         */
        const val RELEASES_URL = "https://api.github.com/repos/UnsleepingDawn/Dascle/releases?per_page=5"
        const val ACCEPT_HEADER = "application/vnd.github+json"
        const val API_VERSION = "2022-11-28"
        const val TIMEOUT_MS = 8_000
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

/** 单次请求的结果，调用方不用关心是直连还是镜像返回的。 */
internal sealed interface ApiResult {

    data class Success(val release: AppRelease) : ApiResult

    data class Failure(val reason: FailureReason) : ApiResult
}
