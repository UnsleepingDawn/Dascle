package com.fitplan.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import com.fitplan.app.R
import com.fitplan.domain.model.ExerciseLoadMode
import com.fitplan.presentation.core.components.material.padding

/**
 * 渐进提示的第一步：这个动作已经按当前目标做满目标组了，要不要把目标提高一点。
 *
 * 可选方向由动作类型决定（见 [ProgressHint.kinds]）：外部负重可以「增加重量」或加次数 / 加时间，
 * 纯自重动作只能加次数 / 加时间，辅助类动作则是「减少辅助重量」或加次数。
 *
 * 按钮放在 `text` 槽位里而不是 `confirmButton`：后者那一行不给宽度约束，
 * `fillMaxWidth` 会失效，按钮会缩成各自文字的宽度、宽窄不一。
 */
@Composable
internal fun ProgressHintDialog(
    hint: ProgressHint,
    onIncrease: (ProgressKind) -> Unit,
    onLater: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.workout_progress_hint_title, hint.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                Text(text = stringResource(R.string.workout_progress_hint_message, hint.completedSets))
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    hint.kinds.forEach { kind ->
                        Button(
                            onClick = { onIncrease(kind) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = increaseLabel(hint = hint, kind = kind))
                        }
                    }
                    TextButton(
                        onClick = onLater,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.workout_progress_hint_later))
                    }
                    TextButton(
                        onClick = onSnooze,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.workout_progress_hint_snooze))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/**
 * 第二步：填要把目标定到多少。这里填的是**绝对值**（不是增量），
 * 输入框留空、上方先报当前基准，「增加到」的语义更明确。
 *
 * 只有比当前基准更难（外部负重更重 / 辅助助力更轻 / 次数时长更多）才能点确定。
 */
@Composable
internal fun ProgressTargetDialog(
    input: ProgressTargetInput,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val hint = input.hint
    val kind = input.kind
    var text by remember(input) { mutableStateOf("") }
    val target = text.toDoubleOrNull()
    val harder = hint.isHarderTarget(kind, target)
    val allowDecimal = kind == ProgressKind.WEIGHT
    val assisted = kind == ProgressKind.WEIGHT && hint.loadMode == ExerciseLoadMode.ASSISTED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.workout_progress_target_title, hint.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                Text(
                    text = stringResource(
                        R.string.workout_progress_target_message,
                        baselineText(hint = hint, kind = kind),
                    ),
                )
                if (assisted) {
                    Text(
                        text = stringResource(R.string.workout_progress_target_note_assist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { new ->
                        text = if (allowDecimal) {
                            new.filter { char -> char.isDigit() || char == '.' }
                        } else {
                            new.filter(Char::isDigit)
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(
                                if (assisted) {
                                    R.string.workout_progress_target_label_assist
                                } else {
                                    R.string.workout_progress_target_label
                                },
                                unitLabel(kind),
                            ),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (target != null && !harder) {
                    Text(
                        text = stringResource(
                            if (assisted) {
                                R.string.workout_progress_target_too_easy_assist
                            } else {
                                R.string.workout_progress_target_too_easy
                            },
                            baselineText(hint = hint, kind = kind),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (harder && target != null) {
                    Text(
                        text = stringResource(
                            R.string.workout_progress_target_preview,
                            baselineText(hint = hint, kind = kind),
                            targetText(hint = hint, kind = kind, target = target),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onConfirm) }, enabled = harder) {
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
 * 第三步：最后确认一次。点「确定」才会真正写进动作库与所有计划编排，
 * 所以这里把「从多少调整到多少」再报一遍。
 */
@Composable
internal fun ProgressConfirmDialog(
    confirm: ProgressTargetConfirm,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hint = confirm.hint
    val kind = confirm.kind
    val assisted = kind == ProgressKind.WEIGHT && hint.loadMode == ExerciseLoadMode.ASSISTED
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.workout_progress_confirm_title, hint.name)) },
        text = {
            Text(
                text = stringResource(
                    if (assisted) {
                        R.string.workout_progress_confirm_message_assist
                    } else {
                        R.string.workout_progress_confirm_message
                    },
                    baselineText(hint = hint, kind = kind),
                    targetText(hint = hint, kind = kind, target = confirm.target),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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

/** 提示弹窗里的按钮文案：辅助类动作的重量方向写成「减少辅助重量」。 */
@Composable
private fun increaseLabel(hint: ProgressHint, kind: ProgressKind): String = stringResource(
    when (kind) {
        ProgressKind.WEIGHT -> if (hint.loadMode == ExerciseLoadMode.ASSISTED) {
            R.string.workout_progress_increase_weight_assist
        } else {
            R.string.workout_progress_increase_weight
        }

        ProgressKind.REPS -> R.string.workout_progress_increase_reps
        ProgressKind.SECONDS -> R.string.workout_progress_increase_seconds
    },
)

@Composable
private fun unitLabel(kind: ProgressKind): String = stringResource(
    when (kind) {
        ProgressKind.WEIGHT -> R.string.unit_kg
        ProgressKind.REPS -> R.string.unit_reps
        ProgressKind.SECONDS -> R.string.unit_seconds
    },
)

/** 当前基准值（带单位）；辅助类动作的重量写成「辅助 40 kg」。 */
@Composable
private fun baselineText(hint: ProgressHint, kind: ProgressKind): String = when (kind) {
    ProgressKind.WEIGHT -> if (hint.loadMode == ExerciseLoadMode.ASSISTED) {
        stringResource(R.string.weight_kg_assist, hint.weightBaseline.toWeightText())
    } else {
        stringResource(R.string.weight_kg, hint.weightBaseline.toWeightText())
    }

    ProgressKind.REPS -> "${hint.repsBaseline} ${stringResource(R.string.unit_reps)}"
    ProgressKind.SECONDS -> "${hint.secondsBaseline} ${stringResource(R.string.unit_seconds)}"
}

/** [target] 这个新目标（带单位）：辅助类动作的重量写成「辅助 40 kg」。 */
@Composable
private fun targetText(hint: ProgressHint, kind: ProgressKind, target: Double): String = when (kind) {
    ProgressKind.WEIGHT -> if (hint.loadMode == ExerciseLoadMode.ASSISTED) {
        stringResource(R.string.weight_kg_assist, target.coerceAtLeast(0.0).toWeightText())
    } else {
        stringResource(R.string.weight_kg, target.toWeightText())
    }

    ProgressKind.REPS -> "${target.toInt()} ${stringResource(R.string.unit_reps)}"
    ProgressKind.SECONDS -> "${target.toInt()} ${stringResource(R.string.unit_seconds)}"
}
