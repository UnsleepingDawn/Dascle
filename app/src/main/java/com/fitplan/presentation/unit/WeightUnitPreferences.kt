package com.fitplan.presentation.unit

import com.fitplan.core.common.preference.Preference
import com.fitplan.core.common.preference.PreferenceStore
import com.fitplan.core.common.preference.getEnum
import com.fitplan.domain.model.WeightUnit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * 训练重量的全局默认单位。
 *
 * 只影响训练重量（动作库默认重量、计划目标重量、每组记录），不影响体重 / 体脂；
 * 单个动作可以在编排页或记录页单独覆盖，没设过的动作跟随这里的值。
 */
@Inject
@SingleIn(AppScope::class)
class WeightUnitPreferences(preferenceStore: PreferenceStore) {

    val weightUnit: Preference<WeightUnit> = preferenceStore.getEnum(KEY_WEIGHT_UNIT, WeightUnit.DEFAULT)

    private companion object {
        const val KEY_WEIGHT_UNIT = "training_weight_unit"
    }
}
