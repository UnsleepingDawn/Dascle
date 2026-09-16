package com.fitplan.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import com.fitplan.domain.model.BodyMetricReminder
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.ui.profile.fieldLabel
import com.fitplan.ui.profile.filterDecimalInput
import com.fitplan.ui.profile.invalidMessage
import com.fitplan.ui.profile.label
import com.fitplan.ui.profile.parseMetric
import com.fitplan.ui.profile.valueText

/**
 * 记录今日体重 / 体脂的输入框。
 *
 * [todayValue] 非空表示今天已经记过这一项，提示用户保存会覆盖它。
 */
@Composable
internal fun BodyMetricInputDialog(
    state: BodyInputState,
    todayValue: Double?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(state) { mutableStateOf(state.prefill) }
    val value = parseMetric(state.field, text)
    val error = text.isNotBlank() && value == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.stats_body_record_title, state.field.label())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = text,
                    onValueChange = { text = it.filterDecimalInput() },
                    label = { Text(text = state.field.fieldLabel()) },
                    isError = error,
                    supportingText = if (error) {
                        { Text(text = state.field.invalidMessage()) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                todayValue?.let {
                    Text(
                        text = stringResource(
                            R.string.stats_body_record_overwrite,
                            state.field.label(),
                            state.field.valueText(it),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value != null,
                onClick = { onConfirm(text) },
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** 超过一周没记录身体数据时的提醒：可以直接去记录，也可以今天不再提。 */
@Composable
internal fun BodyMetricReminderDialog(
    reminder: BodyMetricReminder,
    onRecord: () -> Unit,
    onDismiss: () -> Unit,
) {
    val subject = when {
        reminder.needWeight && reminder.needBodyFat -> stringResource(R.string.stats_body_reminder_subject_both)
        reminder.needWeight -> stringResource(R.string.stats_body_reminder_subject_weight)
        else -> stringResource(R.string.stats_body_reminder_subject_body_fat)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.stats_body_reminder_title)) },
        text = {
            Text(text = stringResource(R.string.stats_body_reminder_message, reminder.daysSinceLastRecord, subject))
        },
        confirmButton = {
            TextButton(onClick = onRecord) {
                Text(text = stringResource(R.string.action_record_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_later))
            }
        },
    )
}
