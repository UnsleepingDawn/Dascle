package com.fitplan.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.fitplan.presentation.core.components.material.padding

/**
 * 日期选择器本身的宽度：Material3 的日期选择器固定按 360dp 排日历（一周七列各 48dp 左右），
 * 比平台默认弹窗宽度（本机约 320dp）宽，所以外框要显式给到 360dp，否则右侧会被裁掉一列。
 */
private val DatePickerWidth = 360.dp

/** 弹窗四周的竖直内边距：顶部与底部各留一点，避免日历与按钮贴住弹窗边缘。 */
private val DialogVerticalPadding = 16.dp

/** 按钮与弹窗左右边缘的间距，和 Material3 弹窗的 24dp 一致。 */
private val DialogHorizontalPadding = 24.dp

/**
 * 「日期选择器 + 一列整宽按钮」的弹窗。
 *
 * 为什么不用 Material3 的 `DatePickerDialog`：它的正文槽位只装得下选择器本身——
 * 再往里塞按钮，按钮会和选择器叠在同一个位置（实测按钮盖住了选择器的「Select date」标题，
 * 把选择器写在后面则按钮整块被盖住，两种顺序都不可用）；而它的操作按钮行不给宽度约束，
 * 按钮没法整宽。所以这里自己搭壳，只借用 [DatePicker] 本体。
 *
 * [DatePicker] 自带「Select date / 选中的日期」两行抬头，所以外框不再另加标题。
 */
@Composable
fun DatePickerButtonsDialog(
    state: DatePickerState,
    onDismissRequest: () -> Unit,
    buttons: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = DialogTonalElevation,
            modifier = Modifier.width(DatePickerWidth),
        ) {
            Column(modifier = Modifier.padding(vertical = DialogVerticalPadding)) {
                DatePicker(state = state, showModeToggle = false)
                DialogButtonColumn(
                    modifier = Modifier.padding(horizontal = DialogHorizontalPadding),
                    content = buttons,
                )
            }
        }
    }
}

/** 与 Material3 弹窗一致的浮起高度。 */
private val DialogTonalElevation = 6.dp
