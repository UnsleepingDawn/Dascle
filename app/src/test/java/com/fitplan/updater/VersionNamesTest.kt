package com.fitplan.updater

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class VersionNamesTest {

    @Test
    fun `更高的版本号算新版`() {
        isNewer("1.2.0", "1.1.1") shouldBe true
        isNewer("2.0.0", "1.9.9") shouldBe true
        isNewer("1.1.2", "1.1.1") shouldBe true
    }

    @Test
    fun `tag 的 v 前缀不影响比较`() {
        isNewer("v1.2.0", "1.1.1") shouldBe true
        isNewer("v1.1.1", "V1.1.1") shouldBe false
    }

    @Test
    fun `debug 包的提交数后缀被忽略`() {
        // debug 变体的 versionName 形如 1.1.1-123，不该被当成新版。
        isNewer("v1.1.1", "1.1.1-123") shouldBe false
        isNewer("1.1.1-124", "1.1.1") shouldBe false
        isNewer("v1.2.0", "1.1.1-123") shouldBe true
    }

    @Test
    fun `元数据后缀被忽略`() {
        isNewer("1.1.1+2", "1.1.1") shouldBe false
    }

    @Test
    fun `位数不同时缺少的段按 0 处理`() {
        isNewer("1.2", "1.1.9") shouldBe true
        isNewer("1.2", "1.2.0") shouldBe false
    }

    @Test
    fun `解析不出数字时一律不算新版`() {
        isNewer("最新版", "1.1.1") shouldBe false
        isNewer("v1.2.x", "1.1.1") shouldBe false
        isNewer("", "1.1.1") shouldBe false
        isNewer("v1.2.0", "") shouldBe false
    }
}
