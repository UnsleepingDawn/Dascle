package com.fitplan.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.fitplan.app.R
import com.fitplan.domain.model.ActivityLevel
import com.fitplan.domain.model.Gender
import com.fitplan.domain.model.ageOn
import com.fitplan.presentation.core.components.material.padding
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * 个人信息表单本体：性别、生日（含推算出的年龄）、身高、体重、体脂率、活动程度。
 * 首次引导页与「我的 → 个人信息」都用它，所有字段都可以留空。
 */
@Composable
internal fun ProfileFormFields(
    form: ProfileFormState,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    var pickingBirthday by remember { mutableStateOf(false) }
    var pickingActivity by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        SectionLabel(text = stringResource(R.string.profile_gender))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
            Gender.entries.forEach { item ->
                FilterChip(
                    selected = form.gender == item,
                    // 再点一次取消选择，回到「不填」。
                    onClick = { form.gender = item.takeIf { form.gender != item } },
                    label = { Text(text = item.label()) },
                )
            }
        }

        BirthdayRow(
            birthday = form.birthday,
            age = ageOn(form.birthday, today),
            onClick = { pickingBirthday = true },
        )

        MetricTextField(
            value = form.heightText,
            onValueChange = { form.heightText = it.filterDecimalInput() },
            label = stringResource(R.string.field_body_height),
            errorMessage = stringResource(R.string.profile_height_invalid).takeIf { form.heightError },
        )

        MetricTextField(
            value = form.weightText,
            onValueChange = { form.weightText = it.filterDecimalInput() },
            label = stringResource(R.string.field_body_weight),
            errorMessage = stringResource(R.string.profile_weight_invalid).takeIf { form.weightError },
        )

        MetricTextField(
            value = form.bodyFatText,
            onValueChange = { form.bodyFatText = it.filterDecimalInput() },
            label = stringResource(R.string.field_body_fat),
            errorMessage = stringResource(R.string.profile_body_fat_invalid).takeIf { form.bodyFatError },
        )

        SectionLabel(text = stringResource(R.string.profile_activity))
        PickerRow(
            label = stringResource(R.string.profile_activity_pick),
            value = form.activityLevel?.label(),
            onClick = { pickingActivity = true },
        )
    }

    if (pickingBirthday) {
        BirthdayPickerDialog(
            initial = form.birthday,
            today = today,
            onPick = { form.birthday = it },
            onDismiss = { pickingBirthday = false },
        )
    }

    if (pickingActivity) {
        ActivityLevelPickerDialog(
            initial = form.activityLevel,
            onPick = { form.activityLevel = it },
            onDismiss = { pickingActivity = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

/** 生日一行：点开日期选择器；右侧显示日期，没填时显示「未填写」，下面补一行周岁。 */
@Composable
private fun BirthdayRow(
    birthday: LocalDate?,
    age: Int?,
    onClick: () -> Unit,
) {
    Column {
        PickerRow(
            label = stringResource(R.string.profile_birthday),
            value = birthday?.toString(),
            onClick = onClick,
        )

        age?.let {
            Text(
                text = stringResource(R.string.profile_age_value, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一行「标题 —— 当前值」，点开弹选择器；[value] 为 null 时显示「未填写」。 */
@Composable
private fun PickerRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value ?: stringResource(R.string.profile_unset),
            style = MaterialTheme.typography.bodyLarge,
            color = if (value == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

/** 日期选择器；[onPick] 传 null 表示「清除」，把生日重新留空。 */
@Composable
private fun BirthdayPickerDialog(
    initial: LocalDate?,
    today: LocalDate,
    onPick: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: today).toUtcMillis(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row {
                if (initial != null) {
                    TextButton(
                        onClick = {
                            onPick(null)
                            onDismiss()
                        },
                    ) {
                        Text(text = stringResource(R.string.action_clear))
                    }
                }
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onPick(it.toUtcDate()) }
                        onDismiss()
                    },
                ) {
                    Text(text = stringResource(R.string.action_ok))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

/**
 * 活动程度单选：五档，每档标题下面带一句说明（「每周运动 1-3 次」这类），
 * 说明太长塞不进一行 chip 里，所以用对话框。已选过时给「清除」把这一项重新留空。
 */
@Composable
private fun ActivityLevelPickerDialog(
    initial: ActivityLevel?,
    onPick: (ActivityLevel?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.profile_activity)) },
        text = {
            Column {
                ActivityLevel.entries.forEach { item ->
                    ActivityLevelOption(
                        level = item,
                        selected = item == initial,
                        onClick = {
                            onPick(item)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {
            if (initial != null) {
                TextButton(
                    onClick = {
                        onPick(null)
                        onDismiss()
                    },
                ) {
                    Text(text = stringResource(R.string.action_clear))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ActivityLevelOption(
    level: ActivityLevel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = MaterialTheme.padding.small)) {
            Text(text = level.label(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = level.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessage: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        isError = errorMessage != null,
        supportingText = errorMessage?.let { text -> { Text(text = text) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toUtcDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
