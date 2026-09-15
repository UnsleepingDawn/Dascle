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
import com.fitplan.presentation.core.components.material.padding

/**
 * 渐进重量提示的第一步：这个动作已经能拿当前重量做满目标组了，要不要加重量。
 *
 * 三个按钮放在 `text` 槽位里而不是 `confirmButton`：后者那一行不给宽度约束，
 * `fillMaxWidth` 会失效，按钮会缩成各自文字的宽度、宽窄不一。
 * 主操作「好的！」用填充按钮，另两个用文字按钮，自上而下依次是「先不加 / 加 / 别再提醒」。
 */
@Composable
internal fun WeightIncreaseHintDialog(
    hint: WeightIncreaseHint,
    onLater: () -> Unit,
    onConfirm: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.workout_weight_hint_title, hint.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                Text(text = stringResource(R.string.workout_weight_hint_message, hint.completedSets))
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    TextButton(
                        onClick = onLater,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.workout_weight_hint_later))
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.workout_weight_hint_ok))
                    }
                    TextButton(
                        onClick = onSnooze,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.workout_weight_hint_snooze))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/**
 * 第二步：填要加多少 kg。确认后的新重量会同时写进动作库与所有计划编排，
 * 所以这里明确报一下当前重量与加完之后的结果。
 */
@Composable
internal fun WeightIncreaseInputDialog(
    hint: WeightIncreaseHint,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val delta = text.toDoubleOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.workout_weight_increase_title, hint.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                Text(
                    text = stringResource(
                        R.string.workout_weight_increase_message,
                        hint.currentWeight.toWeightText(),
                    ),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { char -> char.isDigit() || char == '.' } },
                    label = { Text(text = stringResource(R.string.workout_weight_increase_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                delta?.let {
                    Text(
                        text = stringResource(
                            R.string.workout_weight_increase_preview,
                            (hint.currentWeight + it).toWeightText(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { delta?.let(onConfirm) }, enabled = delta != null) {
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
