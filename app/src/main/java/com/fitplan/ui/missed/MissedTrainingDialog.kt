package com.fitplan.ui.missed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fitplan.app.R
import com.fitplan.domain.interactor.MissedTraining
import com.fitplan.presentation.core.components.material.padding
import kotlinx.datetime.LocalDate

/**
 * 第一步：这段时间练了没有。
 *
 * 只报「起止日期 + 天数」，不列具体哪些日子——用户要的是「怎么处理」，不是对账。
 */
@Composable
fun MissedConfirmDialog(
    missed: MissedTraining,
    onTrained: () -> Unit,
    onNotTrained: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.missed_training_title)) },
        text = {
            ChoiceBody(
                message = if (missed.dayCount == 1) {
                    stringResource(R.string.missed_training_message_one_day, dateText(missed.firstDay))
                } else {
                    stringResource(
                        R.string.missed_training_message,
                        dateText(missed.firstDay),
                        dateText(missed.lastDay),
                        missed.dayCount,
                    )
                },
                choices = listOf(
                    stringResource(R.string.missed_training_did_train) to onTrained,
                    stringResource(R.string.missed_training_did_not_train) to onNotTrained,
                ),
            )
        },
        confirmButton = {},
    )
}

/** 第二步：没练的话，这些计划顺延还是跳过。 */
@Composable
fun MissedHandleDialog(
    onPostpone: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.missed_training_handle_title)) },
        text = {
            ChoiceBody(
                message = stringResource(R.string.missed_training_handle_message),
                choices = listOf(
                    stringResource(R.string.missed_training_postpone) to onPostpone,
                    stringResource(R.string.missed_training_skip) to onSkip,
                ),
            )
        },
        confirmButton = {},
    )
}

/**
 * 说明文字 + 上下排列的两个按钮。
 *
 * 放在 text 槽位里而不是 confirmButton：后者的按钮行不给宽度约束，`fillMaxWidth` 会失效，
 * 两个按钮会缩成各自文字的宽度、宽窄不一。
 */
@Composable
private fun ChoiceBody(message: String, choices: List<Pair<String, () -> Unit>>) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
        Text(text = message)
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            choices.forEach { (text, onClick) ->
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = text)
                }
            }
        }
    }
}

@Composable
private fun dateText(date: LocalDate): String =
    stringResource(R.string.date_month_day, date.month.ordinal + 1, date.day)
