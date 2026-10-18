package com.fitplan.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fitplan.app.R
import com.fitplan.domain.model.WeightUnit
import com.fitplan.presentation.core.components.material.padding

/**
 * 单个动作的重量单位选择器：跟随全局 / 千克 / 磅。
 *
 * 三档用单选行而不是三个按钮：这里表达的是「当前这个动作用哪种单位」，
 * 单选能一眼看出现在选的是哪一档；按钮列举会让人误以为是一组动作。
 *
 * [override] 是动作自己设的单位，null 表示跟随全局；[globalUnit] 只用于把「跟随全局」写成
 * 「跟随全局（kg）」这样的具体文案。
 */
@Composable
internal fun WeightUnitPickerDialog(
    override: WeightUnit?,
    globalUnit: WeightUnit,
    onSelect: (WeightUnit?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.weight_unit_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                Text(
                    text = stringResource(R.string.weight_unit_picker_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // null 排在最前：没特别需要时用全局设置就够了。
                listOf<WeightUnit?>(null, WeightUnit.KG, WeightUnit.LB).forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = MaterialTheme.padding.extraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = override == option,
                            onClick = { onSelect(option) },
                        )
                        Text(
                            text = when (option) {
                                null -> stringResource(
                                    R.string.weight_unit_follow_global,
                                    unitLabel(globalUnit),
                                )

                                WeightUnit.KG -> stringResource(R.string.weight_unit_option_kg)
                                WeightUnit.LB -> stringResource(R.string.weight_unit_option_lb)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = MaterialTheme.padding.small),
                        )
                    }
                }
                DialogButtonColumn {
                    DialogCancelButton(onClick = onDismiss)
                }
            }
        },
        confirmButton = {},
    )
}
