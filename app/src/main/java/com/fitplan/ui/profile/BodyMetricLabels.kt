package com.fitplan.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fitplan.app.R
import com.fitplan.domain.model.Gender

/** 性别的中文名。 */
@Composable
internal fun Gender.label(): String = stringResource(
    when (this) {
        Gender.MALE -> R.string.profile_gender_male
        Gender.FEMALE -> R.string.profile_gender_female
        Gender.OTHER -> R.string.profile_gender_other
    },
)

/** 字段本身的名字，不带单位（「体重」「体脂率」）。 */
@Composable
internal fun BodyMetricField.label(): String = stringResource(
    when (this) {
        BodyMetricField.WEIGHT -> R.string.profile_weight
        BodyMetricField.BODY_FAT -> R.string.profile_body_fat
    },
)

/** 输入框上带单位的名字（「体重（kg）」「体脂率（%）」）。 */
@Composable
internal fun BodyMetricField.fieldLabel(): String = stringResource(
    when (this) {
        BodyMetricField.WEIGHT -> R.string.field_body_weight
        BodyMetricField.BODY_FAT -> R.string.field_body_fat
    },
)

/** 按字段把数值拼成带单位的样子（「70.5 kg」「18.2%」）。 */
@Composable
internal fun BodyMetricField.valueText(value: Double): String = when (this) {
    BodyMetricField.WEIGHT -> stringResource(R.string.weight_kg, formatMetric(value))
    BodyMetricField.BODY_FAT -> stringResource(R.string.body_fat_value, formatMetric(value))
}

/** 数值超出范围时的红字提示。 */
@Composable
internal fun BodyMetricField.invalidMessage(): String = stringResource(
    when (this) {
        BodyMetricField.WEIGHT -> R.string.profile_weight_invalid
        BodyMetricField.BODY_FAT -> R.string.profile_body_fat_invalid
    },
)
