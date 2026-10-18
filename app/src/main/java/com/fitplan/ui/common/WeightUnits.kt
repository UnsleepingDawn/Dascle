package com.fitplan.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.fitplan.app.R
import com.fitplan.domain.model.WeightUnit
import kotlin.math.roundToLong

/**
 * 当前全局默认的训练重量单位，由根组合按设置里的偏好提供。
 *
 * 动作自己设了单位时以动作为准，所以业务代码一般先算出「有效单位」再读这个值：
 * `LocalWeightUnit.current.resolve(exercise.weightUnit)`。
 */
val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.DEFAULT }

/**
 * 把以千克为单位保存的重量转成输入框 / 文案里的文本。
 *
 * 最多保留一位小数、整数不带小数点：磅换算成千克再换回来是 `99.99999999999999` 这种值，
 * 直接 `toString()` 会露出一长串小数。**只在显示时取整，落库仍然写未取整的千克值**，
 * 否则反复切换单位会越漂越远。
 */
internal fun Double?.toWeightText(unit: WeightUnit): String {
    if (this == null) return ""
    val displayed = unit.fromKilograms(this)
    val rounded = (displayed * DECIMAL_SCALE).roundToLong() / DECIMAL_SCALE
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}

/**
 * 把输入框里的文本按 [unit] 解析成千克；空串或非法输入返回 null。
 *
 * 校验「必须大于 0」交给调用方，这里只负责换算，免得把合法输入误判成空。
 */
internal fun String.toKilogramsOrNull(unit: WeightUnit): Double? =
    toDoubleOrNull()?.let { unit.toKilograms(it) }

/** 单位本身的文案，「kg」/「lb」。 */
@Composable
internal fun unitLabel(unit: WeightUnit): String = stringResource(
    when (unit) {
        WeightUnit.KG -> R.string.unit_kg
        WeightUnit.LB -> R.string.unit_lb
    },
)

/** 带单位的重量文案，如「60 kg」「132.3 lb」；辅助类动作前面再加一个「辅助」。 */
@Composable
internal fun weightText(
    valueKg: Double?,
    unit: WeightUnit,
    assisted: Boolean = false,
): String = stringResource(
    if (assisted) R.string.weight_value_assist else R.string.weight_value,
    valueKg.toWeightText(unit),
    unitLabel(unit),
)

/** 显示精度：一位小数。 */
private const val DECIMAL_SCALE = 10.0
