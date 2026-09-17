package com.fitplan.updater

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class GitHubReleaseTest {

    @Test
    fun `优先挑非草稿且非预发布的版本`() {
        val releases = listOf(
            release("v2.0.0", prerelease = true),
            release("v1.9.0"),
            release("v1.8.0"),
        )

        releases.pickLatest()?.tagName shouldBe "v1.9.0"
    }

    @Test
    fun `全是预发布时退回最新的非草稿版本`() {
        // 本仓库 v1.0.0 ~ v1.1.1 都是这个状态，/releases/latest 会 404，这里必须挑得出来。
        val releases = listOf(
            release("v1.1.1", prerelease = true),
            release("v1.1.0", prerelease = true),
        )

        releases.pickLatest()?.tagName shouldBe "v1.1.1"
    }

    @Test
    fun `草稿一律不挑`() {
        val releases = listOf(
            release("v3.0.0", draft = true),
            release("v2.0.0", draft = true, prerelease = true),
            release("v1.0.0"),
        )

        releases.pickLatest()?.tagName shouldBe "v1.0.0"
    }

    @Test
    fun `没有可用版本时返回 null`() {
        emptyList<GitHubRelease>().pickLatest() shouldBe null
        listOf(release("v1.0.0", draft = true)).pickLatest() shouldBe null
    }

    @Test
    fun `转成界面模型时去掉 tag 的 v 前缀并带上镜像前缀`() {
        val release = release("v1.2.3").copy(
            htmlUrl = "https://github.com/o/r/releases/tag/v1.2.3",
            body = "  更新说明  ",
            assets = listOf(
                GitHubReleaseAsset(name = "checksums.txt"),
                GitHubReleaseAsset(name = "Dascle-1.2.3.apk"),
            ),
        )

        val direct = release.toAppRelease(urlPrefix = "")
        direct.version shouldBe "1.2.3"
        direct.pageUrl shouldBe "https://github.com/o/r/releases/tag/v1.2.3"
        direct.notes shouldBe "更新说明"
        direct.apkName shouldBe "Dascle-1.2.3.apk"

        release.toAppRelease(urlPrefix = "https://gh-proxy.com/").pageUrl shouldBe
            "https://gh-proxy.com/https://github.com/o/r/releases/tag/v1.2.3"
    }

    @Test
    fun `能解析 GitHub releases 接口的真实响应`() {
        // 字段名照抄 GitHub 真实返回（含一大堆我们用不到的键），改错 @SerialName 这里就会红。
        // Json 的配置与 AppGraph.providesJson 保持一致。
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        val releases = json.decodeFromString<List<GitHubRelease>>(REAL_RESPONSE)

        releases.size shouldBe 2
        // 两条都被标成「预发布」，靠 pickLatest 的兜底照样挑得出最新的那条。
        val picked = releases.pickLatest().shouldNotBeNull()
        picked.tagName shouldBe "v1.1.1"
        picked.prerelease shouldBe true
        picked.draft shouldBe false

        val release = picked.toAppRelease(urlPrefix = "")
        release.version shouldBe "1.1.1"
        release.pageUrl shouldBe "https://github.com/UnsleepingDawn/Dascle/releases/tag/v1.1.1"
        release.notes shouldBe "本次更新：修复了若干问题"
        release.apkName shouldBe "Dascle-1.1.1.apk"
    }

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) = GitHubRelease(tagName = tag, draft = draft, prerelease = prerelease)

    private companion object {
        /** 摘录自 `GET /repos/UnsleepingDawn/Dascle/releases`，只截短了 body。 */
        val REAL_RESPONSE = """
            [
              {
                "url": "https://api.github.com/repos/UnsleepingDawn/Dascle/releases/390653324",
                "assets_url": "https://api.github.com/repos/UnsleepingDawn/Dascle/releases/390653324/assets",
                "upload_url": "https://uploads.github.com/repos/UnsleepingDawn/Dascle/releases/390653324/assets{?name,label}",
                "html_url": "https://github.com/UnsleepingDawn/Dascle/releases/tag/v1.1.1",
                "id": 390653324,
                "node_id": "RE_kwDOUYAqUM4XSOWM",
                "tag_name": "v1.1.1",
                "target_commitish": "main",
                "name": "v1.1.1",
                "draft": false,
                "prerelease": true,
                "created_at": "2026-09-17T10:39:44Z",
                "published_at": "2026-09-17T11:23:49Z",
                "tarball_url": "https://api.github.com/repos/UnsleepingDawn/Dascle/tarball/v1.1.1",
                "zipball_url": "https://api.github.com/repos/UnsleepingDawn/Dascle/zipball/v1.1.1",
                "body": "本次更新：修复了若干问题",
                "assets": [
                  {
                    "url": "https://api.github.com/repos/UnsleepingDawn/Dascle/releases/assets/570113905",
                    "id": 570113905,
                    "node_id": "RA_kwDOUYAqUM4h-z9x",
                    "name": "Dascle-1.1.1.apk",
                    "label": null,
                    "content_type": "application/vnd.android.package-archive",
                    "state": "uploaded",
                    "size": 8428583,
                    "download_count": 2,
                    "browser_download_url": "https://github.com/UnsleepingDawn/Dascle/releases/download/v1.1.1/Dascle-1.1.1.apk"
                  }
                ],
                "author": { "login": "UnsleepingDawn", "id": 1, "type": "User" }
              },
              {
                "html_url": "https://github.com/UnsleepingDawn/Dascle/releases/tag/v1.1.0",
                "tag_name": "v1.1.0",
                "draft": false,
                "prerelease": true,
                "body": "",
                "assets": [
                  {
                    "name": "Dascle-1.1.0.apk",
                    "browser_download_url": "https://github.com/UnsleepingDawn/Dascle/releases/download/v1.1.0/Dascle-1.1.0.apk"
                  }
                ]
              }
            ]
        """.trimIndent()
    }
}
