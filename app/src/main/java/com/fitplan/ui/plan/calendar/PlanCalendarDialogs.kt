package com.fitplan.ui.plan.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.fitplan.app.R
import com.fitplan.domain.model.Routine
import com.fitplan.presentation.core.components.material.padding

/**
 * 删除已选日子里的计划：确认删完之后怎么处理后面的排期。
 *
 * [plannedCount] 是选中日子里「有健身计划」的天数：恰好一天时用单日措辞（该日休息 / 顺着用未来计划），
 * 多天时用多日措辞（这几日休息 / 已有计划流入）。
 *
 * 两个按钮放在 text 槽位里而不是 confirmButton：后者的按钮行不给宽度约束，`fillMaxWidth` 会失效，
 * 两个按钮会缩成各自文字的宽度、宽窄不一。
 */
@Composable
fun DeleteSelectionDialog(
    plannedCount: Int,
    onRest: () -> Unit,
    onFlow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val single = plannedCount == 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (single) {
                        R.string.calendar_delete_dialog_title_one
                    } else {
                        R.string.calendar_delete_dialog_title_many
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                Text(
                    text = stringResource(
                        if (single) {
                            R.string.calendar_delete_dialog_message_one
                        } else {
                            R.string.calendar_delete_dialog_message_many
                        },
                    ),
                )
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
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
            }
        },
        confirmButton = {},
    )
}

/** 「在该日之前插入…」：选插入休息（可填天数）还是训练计划。 */
@Composable
fun InsertDialog(
    onRest: () -> Unit,
    onTraining: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.calendar_insert_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                Button(onClick = onRest, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.calendar_insert_rest))
                }
                Button(onClick = onTraining, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.calendar_insert_training))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** 插入休息的天数，默认一天，至少要有一天。 */
@Composable
fun InsertRestDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("1") }
    val count = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.calendar_insert_rest_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(2) },
                label = { Text(text = stringResource(R.string.calendar_insert_rest_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = count != null && count >= 1,
                onClick = { count?.let(onConfirm) },
            ) {
                Text(text = stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * 选择要排入的计划。日期明细里的「添加计划」与编辑模式的「计划 / 插入 → 训练计划」共用。
 *
 * 选中之后由调用方负责收起弹窗。
 */
@Composable
fun RoutinePickerDialog(
    routines: List<Routine>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.calendar_pick_routine)) },
        text = {
            if (routines.isEmpty()) {
                Text(text = stringResource(R.string.calendar_no_routine_left))
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}
