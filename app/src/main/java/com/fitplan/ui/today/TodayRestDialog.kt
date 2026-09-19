package com.fitplan.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fitplan.app.R
import com.fitplan.domain.interactor.RestTodayMode
import com.fitplan.presentation.core.components.material.padding
import kotlinx.datetime.LocalDate

/**
 * 今日休息的选择框：今天改成休息日之后，之后排好的训练怎么顺延。
 *
 * 「顺延直到占用下一个休息日」要有可占用的日子才能选——之后不再休息时置灰并给一句提示。
 * [hasOngoingSession] 为 true（今天这次训练还没结束）时点选不立刻执行，先问一句要不要作废，
 * 确认后才把选中的方式交给 [onConfirm]。
 */
@Composable
fun TodayRestDialog(
    nextRestDay: LocalDate?,
    hasOngoingSession: Boolean,
    onConfirm: (RestTodayMode) -> Unit,
    onDismiss: () -> Unit,
) {
    // 有正在进行的训练时，点完顺延方式先停在这一档，等确认作废后再真正执行。
    var pendingMode by remember { mutableStateOf<RestTodayMode?>(null) }

    val choose: (RestTodayMode) -> Unit = { mode ->
        if (hasOngoingSession) pendingMode = mode else onConfirm(mode)
    }

    when (val mode = pendingMode) {
        null -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(R.string.today_rest_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                    Text(text = stringResource(R.string.today_rest_dialog_message))
                    if (hasOngoingSession) {
                        Text(
                            text = stringResource(R.string.today_rest_dialog_discard_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    // 按钮放在 text 槽位里：confirmButton 那一行不给宽度约束，fillMaxWidth 会失效。
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                        Button(
                            onClick = { choose(RestTodayMode.POSTPONE_ALL) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.today_rest_dialog_postpone_all))
                        }
                        Button(
                            onClick = { choose(RestTodayMode.POSTPONE_UNTIL_REST_DAY) },
                            enabled = nextRestDay != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.today_rest_dialog_until_rest))
                        }
                    }
                    if (nextRestDay == null) {
                        Text(
                            text = stringResource(R.string.today_rest_dialog_no_rest),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {},
        )

        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(R.string.today_rest_dialog_discard_title)) },
            text = { Text(text = stringResource(R.string.today_rest_dialog_discard_message)) },
            confirmButton = {
                TextButton(onClick = { onConfirm(mode) }) {
                    Text(text = stringResource(R.string.today_rest_dialog_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
