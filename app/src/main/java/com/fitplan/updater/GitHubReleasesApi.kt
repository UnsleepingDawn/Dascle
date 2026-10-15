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
 * 向 GitHub 查 Release。
 *
 * 只用 JDK 自带的 [HttpURLConnection]，不引入网络库。请求顺序是「直连 api.github.com →
 * 失败且配了镜像前缀 → 走镜像」，两条都不通才把失败原因报上去。
 *
 * 查最新版本用 `GET /releases` 列表接口而不是更省事的 `/releases/latest`：后者只返回
 * 「最新的非草稿、非预发布」的那一条，项目只要把所有版本都标成「预发布」（本仓库的
 * `v1.0.0` ~ `v1.1.1` 就是），它就恒返回 404，网络完全正常也查不到新版本。列表接口稳定
 * 返回非草稿条目，由 [pickLatest] 自己挑。
 *
 * 查「某个具体版本」的更新说明用 `GET /releases/tags/<tag>`，见 [fetchByTag]。
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
        requestWithMirror(endpoint = RELEASES_URL, mirrorPrefix = mirrorPrefix, userAgent = userAgent) { body, prefix ->
            json.decodeFromString<List<GitHubRelease>>(body).pickLatest()?.toAppRelease(prefix).orNoRelease()
        }
    }

    /**
     * 按 tag 查某一条 Release，用来取「当前装的这个版本」的更新说明。
     *
     * tag 命名规范是 `v<versionName>`（见《发布文档》）。仓库里没有这个 tag 时按
     * [FailureReason.NO_RELEASE] 返回，调用方下次启动再试即可。
     */
    suspend fun fetchByTag(tag: String, mirrorPrefix: String, userAgent: String): ApiResult =
        withContext(Dispatchers.IO) {
            val endpoint = "$RELEASES_ROOT/tags/$tag"
            requestWithMirror(endpoint = endpoint, mirrorPrefix = mirrorPrefix, userAgent = userAgent) { body, prefix ->
                val release = json.decodeFromString<GitHubRelease>(body)
                // 草稿不该给用户看，「Generate release notes」还没写完的那种。
                release.takeUnless { it.draft }?.toAppRelease(prefix).orNoRelease()
            }
        }

    /**
     * 先直连，失败且配了镜像前缀时再走镜像重试一次。
     *
     * [parse] 拿到响应正文与实际用的前缀（直连为空），返回 null 表示「服务端没有可用版本」。
     */
    private fun requestWithMirror(
        endpoint: String,
        mirrorPrefix: String,
        userAgent: String,
        parse: (body: String, urlPrefix: String) -> ApiResult,
    ): ApiResult {
        val direct = request(endpoint, userAgent, urlPrefix = "", parse)
        if (direct is ApiResult.Success || mirrorPrefix.isBlank()) return direct

        Log.d(TAG, "直连 GitHub 失败（$direct），改用镜像前缀 $mirrorPrefix")
        return request(endpoint, userAgent, urlPrefix = mirrorPrefix, parse)
    }

    private fun request(
        endpoint: String,
        userAgent: String,
        urlPrefix: String,
        parse: (body: String, urlPrefix: String) -> ApiResult,
    ): ApiResult {
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

                // 仓库不存在、读不到 Release，或者没有这个 tag，重试也没用。
                HttpURLConnection.HTTP_NOT_FOUND -> ApiResult.Failure(FailureReason.NO_RELEASE)

                200 -> parse(connection.inputStream.bufferedReader().use { it.readText() }, urlPrefix)

                else -> {
                    Log.d(TAG, "GitHub 返回 $code（$endpoint）")
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

    /** null 表示「服务端没有可用的版本」，对应 [FailureReason.NO_RELEASE]。 */
    private fun AppRelease?.orNoRelease(): ApiResult =
        this?.let { ApiResult.Success(it) } ?: ApiResult.Failure(FailureReason.NO_RELEASE)

    private companion object {
        const val TAG = "FitPlan"

        const val RELEASES_ROOT = "https://api.github.com/repos/UnsleepingDawn/Dascle/releases"

        /**
         * 用列表接口（`/releases`）而不是 `/releases/latest`，原因见类注释。
         * `per_page` 压到 5：只拿最近几条就够挑出最新的，顺带少传点更新说明。
         */
        const val RELEASES_URL = "$RELEASES_ROOT?per_page=5"
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
