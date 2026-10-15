package com.fitplan.updater

/**
 * 从 Release 正文里解析出的一节：一个标题 + 若干条目。
 *
 * @param category 标题认出来的类别，决定展示顺序与强调程度
 * @param rawTitle 原文标题；空串表示这一节本来就没有标题（正文里直接写了几行条目）
 * @param items 条目文本，已经去掉 markdown 标记
 */
data class ReleaseNotesSection(
    val category: ReleaseNotesCategory,
    val rawTitle: String,
    val items: List<String>,
)

/**
 * 更新说明的类别，[importance] 越小排得越靠前、展示时越醒目。
 *
 * 只有 [OTHER] 需要在界面上原样显示 [ReleaseNotesSection.rawTitle]，
 * 其余类别由界面按类别取中文名（文案在 `strings.xml`）。
 */
enum class ReleaseNotesCategory(val importance: Int) {
    /** 新增的功能。 */
    FEATURE(1),

    /** 行为调整、优化。 */
    CHANGE(2),

    /** 修 bug。 */
    FIX(3),

    /** 删掉的东西。 */
    REMOVAL(4),

    /** 文档、内部整理。 */
    DOCS(5),

    /** 认不出的标题，或者根本没有标题的裸列表，一律排到最后。 */
    OTHER(6),
}

/**
 * 把 GitHub Release 的正文解析成按重要度排好序的分节。
 *
 * 发版时按 `## 新增` / `## 调整` / `## 修复` 分节写（见《发布文档》），这里同时认
 * markdown 的 `#` ~ `######` 标题与 `-` / `*` / `+` 条目，并把 GitHub「Generate release
 * notes」自动生成的英文标题（`Features` / `Bug Fixes` 之类）归到同一批类别里。
 *
 * 解析不出内容时返回空列表，调用方按「这个版本没写更新说明」处理。
 */
fun parseReleaseNotes(raw: String): List<ReleaseNotesSection> {
    val pending = mutableListOf<PendingSection>()
    var current: PendingSection? = null

    raw.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || SEPARATOR_PATTERN.matches(trimmed)) return@forEach

        val heading = HEADING_PATTERN.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
        if (heading != null) {
            current = PendingSection(title = heading, items = mutableListOf()).also(pending::add)
            return@forEach
        }

        val text = cleanInlineMarkup(LIST_ITEM_PATTERN.matchEntire(trimmed)?.groupValues?.get(1) ?: trimmed)
        if (text.isEmpty()) return@forEach

        // 标题之前直接出现的条目算一个没有标题的分节（最后归到「其他」）。
        val section = current ?: PendingSection(title = "", items = mutableListOf()).also {
            pending += it
            current = it
        }
        section.items += text
    }

    return pending
        .filterNot { it.title.isDiscardedTitle() }
        .filter { it.items.isNotEmpty() }
        .map { ReleaseNotesSection(category = categorize(it.title), rawTitle = it.title, items = it.items) }
        .sortedBy { it.category.importance }
}

/** 解析过程中的一节；[title] 为空表示还没遇到标题。 */
private class PendingSection(val title: String, val items: MutableList<String>)

/** 标题 → 类别。只做归一化匹配，认不出来的一律算 [ReleaseNotesCategory.OTHER]。 */
private fun categorize(title: String): ReleaseNotesCategory =
    CATEGORY_ALIASES[title.trim().lowercase()] ?: ReleaseNotesCategory.OTHER

/**
 * 要不要丢掉这一节。
 *
 * 《发布文档》里的更新说明模板带一段「安装」说明，GitHub 自动生成的正文还带
 * 「New Contributors」与「Full Changelog」——这些都是给下载页看的，不该进 App 里的弹窗。
 */
private fun String.isDiscardedTitle(): Boolean = trim().lowercase() in DISCARDED_TITLES

/** 去掉 markdown 标记，把 GitHub 自动带上的「by @某人 in <链接>」尾巴也摘掉。 */
private fun cleanInlineMarkup(raw: String): String =
    raw
        .replace(GITHUB_ATTRIBUTION_PATTERN, "")
        .replace(MARKDOWN_LINK_PATTERN) { it.groupValues[1] }
        .replace(INLINE_MARKUP_PATTERN, "")
        .replace(WHITESPACE_PATTERN, " ")
        .trim()

private val HEADING_PATTERN = Regex("""^#{1,6}\s*(.+?)\s*#*$""")

private val LIST_ITEM_PATTERN = Regex("""^[-*+]\s+(.+)$""")

/** 只有分隔线的行（`---` / `***` / `___`）。 */
private val SEPARATOR_PATTERN = Regex("""^(?:[-*_]\s*){3,}$""")

private val GITHUB_ATTRIBUTION_PATTERN = Regex("""\s+by\s+@\S+\s+in\s+https?://\S+$""")

private val MARKDOWN_LINK_PATTERN = Regex("""\[([^\]]*)]\([^)]*\)""")

/** 行内强调用的标记字符；标题已经被切掉了，剩下的都可以删。 */
private val INLINE_MARKUP_PATTERN = Regex("""[*_`~]""")

private val WHITESPACE_PATTERN = Regex("""\s+""")

/** 标题别名表，键是小写去空格的标题。 */
private val CATEGORY_ALIASES: Map<String, ReleaseNotesCategory> = buildMap {
    fun alias(category: ReleaseNotesCategory, vararg titles: String) {
        titles.forEach { this[it.lowercase()] = category }
    }

    alias(
        ReleaseNotesCategory.FEATURE,
        "新增",
        "新功能",
        "新特性",
        "features",
        "feature",
        "new features",
        "added",
        "what's new",
    )
    alias(
        ReleaseNotesCategory.CHANGE,
        "调整",
        "改进",
        "优化",
        "变更",
        "changes",
        "changed",
        "improvements",
        "improvement",
        "what's changed",
        "whats changed",
    )
    alias(
        ReleaseNotesCategory.FIX,
        "修复",
        "修正",
        "bug fixes",
        "bug fix",
        "bugs",
        "fixes",
        "fix",
        "fixed",
    )
    alias(ReleaseNotesCategory.REMOVAL, "移除", "删除", "清理", "removals", "removal", "removed")
    alias(ReleaseNotesCategory.DOCS, "整理", "文档", "documentation", "docs")
}

private val DISCARDED_TITLES: Set<String> = setOf(
    "安装",
    "安装说明",
    "说明",
    "install",
    "installation",
    "new contributors",
    "contributors",
    "full changelog",
)
