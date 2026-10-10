package com.fitplan.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fitplan.app.R
import com.fitplan.presentation.core.components.material.padding

/**
 * 弹窗里的一列操作按钮：整宽、竖排、固定间距。
 *
 * 为什么不用 `AlertDialog` 的 `confirmButton` / `dismissButton`：那一行不给宽度约束，
 * `fillMaxWidth()` 会失效、按钮缩成各自文字的宽度、宽窄不一；而且两个槽位只能各放一个按钮，
 * 三个及以上就没处放。所以全项目统一约定：弹窗里 `confirmButton = {}`（留空），
 * 操作按钮一律塞进 `text` 槽位，外面套一层 [DialogButtonColumn]，需要几个放几个。
 */
@Composable
fun DialogButtonColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        content = content,
    )
}

/** 确认 / 保存 / 选择类按钮：实底主色、整宽。 */
@Composable
fun DialogPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = text)
    }
}

/**
 * 结束 / 放弃 / 删除 / 移除 / 作废类按钮：实底 error 色，与训练记录页的「结束训练」按钮同款、整宽。
 */
@Composable
fun DialogDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = text)
    }
}

/** 弹窗里的「取消」：全项目唯一保留描边样式的按钮，文案固定为 `action_cancel`、整宽。 */
@Composable
fun DialogCancelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.action_cancel))
    }
}
