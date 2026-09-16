package com.fitplan.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fitplan.app.R
import com.fitplan.domain.model.BodyMetric
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.screens.LoadingScreen
import com.fitplan.presentation.util.Screen
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 「我的 → 个人信息」：看当前数据，也能直接改。
 *
 * 性别 / 生日是画像本身；体重 / 体脂率改了会写成今天的记录（当天已经有就覆盖），
 * 留空则表示这次不动这一项。
 */
object ProfileScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = metroViewModel<ProfileScreenModel>()
        val state by screenModel.state.collectAsState()
        val saved by screenModel.saved.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val savedMessage = stringResource(R.string.profile_saved)

        val form = remember { ProfileFormState() }
        var initialized by remember { mutableStateOf(false) }

        // 表单一进来就填上当前值，之后不再覆盖用户正在敲的内容（保存后刷新也不动它）。
        LaunchedEffect(state) {
            val current = state
            if (current != null && !initialized) {
                form.gender = current.profile.gender
                form.birthday = current.profile.birthday
                form.weightText = current.latestWeight?.weight?.let { formatMetric(it) }.orEmpty()
                form.bodyFatText = current.latestBodyFat?.bodyFat?.let { formatMetric(it) }.orEmpty()
                initialized = true
            }
        }

        LaunchedEffect(saved) {
            if (saved) {
                snackbarHostState.showSnackbar(savedMessage)
                screenModel.consumeSaved()
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.profile_title)) },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            val current = state
            if (current == null) {
                LoadingScreen()
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(
                        start = MaterialTheme.padding.medium,
                        end = MaterialTheme.padding.medium,
                        bottom = MaterialTheme.padding.medium,
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                CurrentDataCard(weight = current.latestWeight, bodyFat = current.latestBodyFat)

                ProfileFormFields(form = form, today = today())

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = form.canSave,
                    onClick = {
                        screenModel.save(
                            gender = form.gender,
                            birthday = form.birthday,
                            weight = form.weight,
                            bodyFat = form.bodyFat,
                        )
                    },
                ) {
                    Text(text = stringResource(R.string.action_save))
                }
            }
        }
    }
}

/** 当前数据：最近一次体重与体脂率，附上各自记录的日期。 */
@Composable
private fun CurrentDataCard(
    weight: BodyMetric?,
    bodyFat: BodyMetric?,
) {
    val unset = stringResource(R.string.profile_unset)

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
        Text(
            text = stringResource(R.string.profile_current),
            style = MaterialTheme.typography.titleSmall,
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                MetricRow(
                    label = stringResource(R.string.profile_weight),
                    value = weight?.weight?.let { stringResource(R.string.weight_kg, formatMetric(it)) } ?: unset,
                    date = weight?.date,
                )
                MetricRow(
                    label = stringResource(R.string.profile_body_fat),
                    value = bodyFat?.bodyFat?.let { stringResource(R.string.body_fat_value, formatMetric(it)) }
                        ?: unset,
                    date = bodyFat?.date,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    date: LocalDate?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = date?.let { stringResource(R.string.stats_body_last_record, formatDate(it)) }
                    ?: stringResource(R.string.stats_body_never_recorded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 日期写全，跨年的记录才不会看成今年。 */
internal fun formatDate(date: LocalDate): String = "${date.year}/${date.month.ordinal + 1}/${date.day}"

internal fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
