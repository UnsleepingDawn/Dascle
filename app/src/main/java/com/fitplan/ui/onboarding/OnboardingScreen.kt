package com.fitplan.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fitplan.app.R
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.ui.profile.ProfileFormFields
import com.fitplan.ui.profile.ProfileFormState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 首次打开 App 时的个人信息引导页：性别、生日、体重、体脂率，全部可以留空。
 *
 * 这一页不挂在 Navigator 上（`MainActivity` 直接根据引导状态选页面），所以没有返回键，
 * 退出只有「保存并开始」与「跳过」两条路。
 */
@Composable
fun OnboardingScreen(
    onSave: (ProfileFormState) -> Unit,
    onSkip: () -> Unit,
) {
    val form = remember { ProfileFormState() }
    val today = remember { today() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.padding.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ProfileFormFields(form = form, today = today)

        Text(
            text = stringResource(R.string.profile_optional_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            TextButton(
                modifier = Modifier.weight(1f),
                onClick = onSkip,
            ) {
                Text(text = stringResource(R.string.onboarding_skip))
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = form.canSave,
                onClick = { onSave(form) },
            ) {
                Text(text = stringResource(R.string.onboarding_done))
            }
        }
    }
}

private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
