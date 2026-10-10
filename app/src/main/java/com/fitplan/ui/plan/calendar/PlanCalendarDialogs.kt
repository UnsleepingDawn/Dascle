package com.fitplan.ui.plan.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitplan.app.R
import com.fitplan.domain.model.Routine
import com.fitplan.presentation.core.components.material.padding

/**
 * 编辑模式各弹窗共用的紧凑外框。
 *
 * Material3 的 `AlertDialog` 会在「正文」与「操作按钮」两处各留一份 24dp 的下内边距，按钮下方因此空出
 * 近 48dp，短弹窗显得很空。这里改用 [BasicAlertDialog] 自己搭壳：下内边距收到 [DialogBottomPadding]，
 * 标题与内容、内容与按钮一律 16dp 间距，得到「紧凑但不局促」的排布；宽度与 Material3 弹窗一致。
 *
 * 正文沿用 Material3 弹窗的口径（`bodyMedium` + `onSurfaceVariant`），按钮与输入框自带配色字级不受影响。
 */
@Composable
private fun CompactDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = DialogTonalElevation,
            modifier = Modifier.sizeIn(minWidth = DialogMinWidth, maxWidth = DialogMaxWidth),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = DialogSidePadding,
                    end = DialogSidePadding,
                    top = DialogTopPadding,
                    bottom = DialogBottomPadding,
                ),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = MaterialTheme.padding.medium),
                )
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 删除已选日子的排期：确认删完之后怎么处理后面的排期。
 *
 * [plannedCount] 是选中日子里「有健身计划」的天数：为 0 表示选中的全是休息日，删除它们只会把
 * 后续排期整体提前（[onRemoveRests]），没有「撤掉计划」一说；大于 0 时按恰好一天用单日措辞
 * （该日休息 / 顺着用未来计划）、多天用多日措辞（这几日休息 / 已有计划流入），二者都保留。
 *
 * 操作按钮整宽竖排：Material3 的按钮行不给宽度约束，`fillMaxWidth` 会失效，整宽按钮会缩成
 * 各自文字的宽度、宽窄不一。
 */
@Composable
fun DeleteSelectionDialog(
    plannedCount: Int,
    selectedCount: Int,
    onRest: () -> Unit,
    onFlow: () -> Unit,
    onRemoveRests: () -> Unit,
    onDismiss: () -> Unit,
) {
    val restOnly = plannedCount == 0
    val single = if (restOnly) selectedCount == 1 else plannedCount == 1
    CompactDialog(
        onDismissRequest = onDismiss,
        title = stringResource(
            when {
                restOnly && single -> R.string.calendar_delete_rest_dialog_title_one
                restOnly -> R.string.calendar_delete_rest_dialog_title_many
                single -> R.string.calendar_delete_dialog_title_one
                else -> R.string.calendar_delete_dialog_title_many
            },
        ),
    ) {
        Text(
            text = stringResource(
                when {
                    restOnly && single -> R.string.calendar_delete_rest_dialog_message_one
                    restOnly -> R.string.calendar_delete_rest_dialog_message_many
                    single -> R.string.calendar_delete_dialog_message_one
                    else -> R.string.calendar_delete_dialog_message_many
                },
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            if (restOnly) {
                Button(onClick = onRemoveRests, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.action_ok))
                }
            } else {
                Button(onClick = onRest, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            if (single) {
                                R.string.calendar_delete_dialog_rest_one
                            } else {
                                R.string.calendar_delete_dialog_rest_many
                            },
                        ),
                    )
                }
                Button(onClick = onFlow, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            if (single) {
                                R.string.calendar_delete_dialog_flow_one
                            } else {
                                R.string.calendar_delete_dialog_flow_many
                            },
                        ),
                    )
                }
            }
            DialogCancelButton(onClick = onDismiss)
        }
    }
}

/** 弹窗里的「取消」：描边样式、整宽居中，高度与上方操作按钮一致。 */
@Composable
private fun DialogCancelButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.action_cancel))
    }
}

/**
 * 「在该日之前插入…」：选插入休息（可填天数）还是训练计划。
 *
 * 「取消」与两个操作按钮一起竖排，凑成三个等宽按钮；不放到 Material3 的按钮行里
 * ——那里既不与操作按钮等宽、又会被顶到弹窗最底部、还右对齐。
 */
@Composable
fun InsertDialog(
    onRest: () -> Unit,
    onTraining: () -> Unit,
    onDismiss: () -> Unit,
) {
    CompactDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.calendar_insert_title),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            Button(onClick = onRest, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.calendar_insert_rest))
            }
            Button(onClick = onTraining, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.calendar_insert_training))
            }
            DialogCancelButton(onClick = onDismiss)
        }
    }
}

/** 插入休息的天数，默认一天，至少要有一天。 */
@Composable
fun InsertRestDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("1") }
    val count = text.toIntOrNull()

    CompactDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.calendar_insert_rest_title),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter(Char::isDigit).take(2) },
            label = { Text(text = stringResource(R.string.calendar_insert_rest_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            Button(
                onClick = { count?.let(onConfirm) },
                enabled = count != null && count >= 1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.action_ok))
            }
            DialogCancelButton(onClick = onDismiss)
        }
    }
}

/**
 * 选择要排入的计划。日期明细里的「添加计划」与编辑模式的「计划 / 插入 → 训练计划」共用，
 * 标题由调用方传入以区分入口。
 *
 * 选中之后由调用方负责收起弹窗。
 */
@Composable
fun RoutinePickerDialog(
    title: String,
    routines: List<Routine>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    CompactDialog(onDismissRequest = onDismiss, title = title) {
        if (routines.isEmpty()) {
            Text(text = stringResource(R.string.calendar_no_routine_left))
        } else {
            Column(
                modifier = Modifier
                    .heightIn(max = RoutineListMaxHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                routines.forEach { routine ->
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(routine.id) }
                            .padding(vertical = MaterialTheme.padding.small),
                    )
                }
            }
        }
        DialogCancelButton(onClick = onDismiss)
    }
}

/** 弹窗四周的内边距：左右对齐 Material3 的 24dp，上下收紧。 */
private val DialogSidePadding = 24.dp
private val DialogTopPadding = 20.dp
private val DialogBottomPadding = 16.dp
private val DialogTonalElevation = 6.dp

/** 与 Material3 弹窗一致的宽度区间。 */
private val DialogMinWidth = 280.dp
private val DialogMaxWidth = 560.dp

/** 计划列表再长也要给取消按钮留位置，超出就滚动。 */
private val RoutineListMaxHeight = 320.dp
