package com.fitplan.presentation.core.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fitplan.presentation.core.components.material.padding

/**
 * 统计页等页面用的分组卡片：可选一行小标题，下面是内容。
 *
 * 圆角取 `large`（16dp），与记录页的卡片、热身提示条一致；原来的 `extraLarge`（28dp）在手机上显得过圆。
 */
@Composable
fun LazyItemScope.SectionCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (title != null) {
        Text(
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.extraLarge),
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
            content()
        }
    }
}
