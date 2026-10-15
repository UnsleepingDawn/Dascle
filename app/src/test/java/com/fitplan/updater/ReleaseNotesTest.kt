package com.fitplan.updater

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReleaseNotesTest {

    @Test
    fun `分节按重要度重排`() {
        val raw = """
            ## 修复
            - 修了一个问题

            ## 安装
            下载 APK 覆盖安装。

            ## 新增
            - 加了一个功能

            ## 调整
            - 改了一处交互
        """.trimIndent()

        parseReleaseNotes(raw).map { it.category } shouldBe
            listOf(ReleaseNotesCategory.FEATURE, ReleaseNotesCategory.CHANGE, ReleaseNotesCategory.FIX)
    }

    @Test
    fun `英文标题归到对应类别`() {
        val raw = """
            ## Bug Fixes
            - fix a crash

            ## What's Changed
            - tweak something

            ## New Features
            - add something
        """.trimIndent()

        parseReleaseNotes(raw).map { it.category } shouldBe
            listOf(ReleaseNotesCategory.FEATURE, ReleaseNotesCategory.CHANGE, ReleaseNotesCategory.FIX)
    }

    @Test
    fun `同档的节保持正文原顺序`() {
        val raw = """
            ## 修复
            - 先写的

            ## 调整
            - 夹在中间的

            ## 新增
            - 第一条

            ## 新增
            - 第二条
        """.trimIndent()

        parseReleaseNotes(raw).flatMap { it.items } shouldBe
            listOf("第一条", "第二条", "夹在中间的", "先写的")
    }

    @Test
    fun `丢掉安装说明与 GitHub 自动生成的尾巴`() {
        val raw = """
            ## 新增
            - 加了一个功能

            ## New Contributors
            - @someone made their first contribution in https://github.com/o/r/pull/9

            ## 安装
            下载 APK 覆盖安装。

            **Full Changelog**: https://github.com/o/r/compare/v1.0.0...v1.1.0
        """.trimIndent()

        val sections = parseReleaseNotes(raw)
        sections.size shouldBe 1
        sections.single().category shouldBe ReleaseNotesCategory.FEATURE
    }

    @Test
    fun `去掉条目里的 markdown 标记与作者尾巴`() {
        val raw = """
            ## 修复
            - **修复**了日历的高亮 [见 #12](https://github.com/o/r/pull/12) by @someone in https://github.com/o/r/pull/12
        """.trimIndent()

        parseReleaseNotes(raw).single().items shouldBe listOf("修复了日历的高亮 见 #12")
    }

    @Test
    fun `没有标题的裸列表归到其他且标题为空`() {
        val sections = parseReleaseNotes(
            """
            - 一行说明
            - 又一行说明
            """.trimIndent(),
        )

        sections shouldBe listOf(
            ReleaseNotesSection(
                category = ReleaseNotesCategory.OTHER,
                rawTitle = "",
                items = listOf("一行说明", "又一行说明"),
            ),
        )
    }

    @Test
    fun `认不出的标题归到其他并保留原文`() {
        val sections = parseReleaseNotes("## 性能\n- 快了很多")

        sections.single().category shouldBe ReleaseNotesCategory.OTHER
        sections.single().rawTitle shouldBe "性能"
    }

    @Test
    fun `整段没有标题也没有条目的正文兜底成一条`() {
        parseReleaseNotes("本次更新：修复了若干问题").single().items shouldBe listOf("本次更新：修复了若干问题")
    }

    @Test
    fun `只有标题没有条目的分节不展示`() {
        parseReleaseNotes("## 新增\n\n## 修复\n- 修了一个问题").map { it.rawTitle } shouldBe listOf("修复")
    }

    @Test
    fun `分隔线与空行被忽略`() {
        parseReleaseNotes("## 新增\n---\n- 加了一个功能\n\n").single().items shouldBe listOf("加了一个功能")
    }

    @Test
    fun `空正文解析成空列表`() {
        parseReleaseNotes("") shouldBe emptyList()
        parseReleaseNotes("   \n\n  ") shouldBe emptyList()
    }
}
