package com.fitplan.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitplan.app.R
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.updater.ReleaseNotesCategory
import com.fitplan.updater.ReleaseNotesSection

/** 更新说明在弹窗里的最大高度，超出就滚动，别把弹窗撑爆。 */
val ReleaseNotesMaxHeight = 280.dp

/**
 * 渲染解析好的更新说明：一节一个「色条 + 标题」，条目排成圆点列表。
 *
 * 颜色按 [ReleaseNotesCategory.importance] 取：越靠前的类别越醒目，靠后的（移除 / 整理 / 其他）
 * 一律用次要色，避免弹窗里一片花花绿绿。类别的中文名取 `strings.xml`；认不出的类别
 * （[ReleaseNotesCategory.OTHER]）才原样显示正文里的标题，没有标题就只列条目。
 *
 * 只负责排版，滚动与限高由调用方套在外层。
 */
@Composable
fun ReleaseNotesContent(
    sections: List<ReleaseNotesSection>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.large),
    ) {
        sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                ReleaseNotesSectionTitle(section)
                section.items.forEach { item ->
                    ReleaseNotesItem(text = item, color = section.category.accentColor())
                }
            }
        }
    }
}

@Composable
private fun ReleaseNotesSectionTitle(section: ReleaseNotesSection) {
    val title = when (section.category) {
        // 认不出的标题原样显示；空串表示正文里没有分节，只列条目。
        ReleaseNotesCategory.OTHER -> section.rawTitle
        else -> stringResource(section.category.titleRes())
    }
    if (title.isBlank()) return

    val color = section.category.accentColor()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Box(
            modifier = Modifier
                .size(width = SectionBarWidth, height = SectionBarHeight)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}

@Composable
private fun ReleaseNotesItem(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Box(
            modifier = Modifier
                .padding(top = BulletTopPadding)
                .size(BulletSize)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReleaseNotesCategory.accentColor(): Color = when (this) {
    ReleaseNotesCategory.FEATURE -> MaterialTheme.colorScheme.primary
    ReleaseNotesCategory.CHANGE -> MaterialTheme.colorScheme.tertiary

    // 用 error 而不是 secondary：本项目三套配色里 secondary 与 primary 是同一个颜色
    // （见 FitPlanColorScheme），拿它标「修复」会跟「新增」一模一样，分不开。
    ReleaseNotesCategory.FIX -> MaterialTheme.colorScheme.error

    ReleaseNotesCategory.REMOVAL,
    ReleaseNotesCategory.DOCS,
    ReleaseNotesCategory.OTHER,
    -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun ReleaseNotesCategory.titleRes(): Int = when (this) {
    ReleaseNotesCategory.FEATURE -> R.string.changelog_category_feature
    ReleaseNotesCategory.CHANGE -> R.string.changelog_category_change
    ReleaseNotesCategory.FIX -> R.string.changelog_category_fix
    ReleaseNotesCategory.REMOVAL -> R.string.changelog_category_removal
    ReleaseNotesCategory.DOCS -> R.string.changelog_category_docs
    ReleaseNotesCategory.OTHER -> R.string.changelog_category_other
}

private val SectionBarWidth = 3.dp
private val SectionBarHeight = 14.dp
private val BulletSize = 5.dp
private val BulletTopPadding = 6.dp
