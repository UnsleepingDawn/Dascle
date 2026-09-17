package com.fitplan.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fitplan.domain.model.ActivityLevel
import com.fitplan.domain.model.Gender
import kotlinx.datetime.LocalDate

/** 体重 / 体脂率这两项身体数据，记录对话框与个人信息页按它决定标签与单位。 */
enum class BodyMetricField {
    WEIGHT,
    BODY_FAT,
}

/**
 * 个人信息表单的可变状态，首次引导页与「我的 → 个人信息」共用。
 *
 * 字段都允许留空：文本框为空代表「不填」，非空但解析不出合法数值才算填错。
 * [weight] / [bodyFat] / [height] 返回的是「可以写入数据库的值」，留空或填错时都是 null，
 * 由页面用 [weightError] / [bodyFatError] / [heightError] 决定要不要红字提示。
 */
class ProfileFormState(
    gender: Gender? = null,
    birthday: LocalDate? = null,
    heightText: String = "",
    activityLevel: ActivityLevel? = null,
    weightText: String = "",
    bodyFatText: String = "",
) {

    var gender by mutableStateOf(gender)

    var birthday by mutableStateOf(birthday)

    var heightText by mutableStateOf(heightText)

    var activityLevel by mutableStateOf(activityLevel)

    var weightText by mutableStateOf(weightText)

    var bodyFatText by mutableStateOf(bodyFatText)

    /** 身高（cm）；留空或填错时为 null。 */
    val height: Double? get() = parseHeight(heightText)

    /** 体重（kg）；留空或填错时为 null。 */
    val weight: Double? get() = parseMetric(BodyMetricField.WEIGHT, weightText)

    /** 体脂率（%）；留空或填错时为 null。 */
    val bodyFat: Double? get() = parseMetric(BodyMetricField.BODY_FAT, bodyFatText)

    val heightError: Boolean get() = heightText.isNotBlank() && height == null

    val weightError: Boolean get() = weightText.isNotBlank() && weight == null

    val bodyFatError: Boolean get() = bodyFatText.isNotBlank() && bodyFat == null

    /** 填错的时候不让保存，免得把明显不对的数写进记录。 */
    val canSave: Boolean get() = !heightError && !weightError && !bodyFatError
}

/** 把 100.0 这种整数值显示成「100」，其余保持原样。 */
internal fun formatMetric(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/**
 * 解析身体数据输入框里的文本：留空、不是数字、或超出合理范围都返回 null。
 * 个人信息表单与统计页的记录对话框共用这一处范围判断。
 */
internal fun parseMetric(field: BodyMetricField, text: String): Double? = text.toDoubleOrNull()?.takeIf { value ->
    when (field) {
        BodyMetricField.WEIGHT -> value in MIN_WEIGHT..MAX_WEIGHT
        BodyMetricField.BODY_FAT -> value in MIN_BODY_FAT..MAX_BODY_FAT
    }
}

/** 解析身高输入框里的文本：留空、不是数字、或超出合理范围都返回 null。 */
internal fun parseHeight(text: String): Double? =
    text.toDoubleOrNull()?.takeIf { it in MIN_HEIGHT..MAX_HEIGHT }

/** 体重 / 体脂 / 身高输入框的按键过滤：只留数字与小数点，顺带限长。 */
internal fun String.filterDecimalInput(): String = filter { it.isDigit() || it == '.' }.take(MAX_INPUT_LENGTH)

private const val MAX_INPUT_LENGTH = 6

private const val MIN_WEIGHT = 1.0
private const val MAX_WEIGHT = 499.0
private const val MIN_BODY_FAT = 0.0
private const val MAX_BODY_FAT = 100.0
private const val MIN_HEIGHT = 50.0
private const val MAX_HEIGHT = 300.0
