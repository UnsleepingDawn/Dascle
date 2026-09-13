package com.fitplan.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fitplan.app.R
import com.fitplan.presentation.core.components.CheckboxItem
import com.fitplan.presentation.core.components.IconItem
import com.fitplan.presentation.core.components.SettingsItemsPaddings
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.components.material.padding
import com.fitplan.presentation.core.util.secondaryItemAlpha
import com.fitplan.presentation.util.Screen
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** 设置页：目前只有训练提醒；没有权限时给出降级提示与一键授权入口。 */
object SettingsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = metroViewModel<SettingsScreenModel>()
        val enabled by screenModel.reminderEnabled.collectAsState()
        val hour by screenModel.reminderHour.collectAsState()
        val minute by screenModel.reminderMinute.collectAsState()

        var showTimePicker by remember { mutableStateOf(false) }
        var notificationsAllowed by remember { mutableStateOf(screenModel.canPostNotifications()) }
        var exactAlarmsAllowed by remember { mutableStateOf(screenModel.canScheduleExactAlarms()) }

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { notificationsAllowed = screenModel.canPostNotifications() }

        LaunchedEffect(Unit) {
            notificationsAllowed = screenModel.canPostNotifications()
            exactAlarmsAllowed = screenModel.canScheduleExactAlarms()
        }

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.settings_title)) },
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
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
            ) {
                Text(
                    text = stringResource(R.string.settings_reminder_heading),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        start = SettingsItemsPaddings.Horizontal,
                        end = SettingsItemsPaddings.Horizontal,
                        top = MaterialTheme.padding.medium,
                    ),
                )

                CheckboxItem(
                    label = stringResource(R.string.settings_reminder_switch),
                    checked = enabled,
                    onClick = {
                        val next = !enabled
                        screenModel.setReminderEnabled(next)
                        if (next && !notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (next) {
                            exactAlarmsAllowed = screenModel.canScheduleExactAlarms()
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.settings_reminder_switch_desc),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(
                            start = SettingsItemsPaddings.Horizontal,
                            end = SettingsItemsPaddings.Horizontal,
                        )
                        .secondaryItemAlpha(),
                )

                if (enabled) {
                    IconItem(
                        label = stringResource(
                            R.string.settings_reminder_time,
                            "%02d:%02d".format(hour, minute),
                        ),
                        icon = Icons.Filled.Alarm,
                        onClick = { showTimePicker = true },
                    )

                    if (!notificationsAllowed) {
                        ReminderHint(
                            text = stringResource(R.string.settings_reminder_notifications_off),
                            action = stringResource(R.string.settings_reminder_grant_notifications),
                            // 已经被拒绝过（否则开关打开时就会弹系统对话框），这里直接去系统设置，
                            // 免得「拒绝过两次后系统不再弹窗」导致点按钮没反应。
                            onClick = { context.startActivity(appNotificationSettingsIntent(context)) },
                        )
                    }

                    if (!exactAlarmsAllowed) {
                        ReminderHint(
                            text = stringResource(R.string.settings_reminder_exact_off),
                            action = stringResource(R.string.settings_reminder_grant_exact),
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(Uri.fromParts("package", context.packageName, null)),
                                )
                                exactAlarmsAllowed = screenModel.canScheduleExactAlarms()
                            },
                        )
                    }
                }
            }
        }

        if (showTimePicker) {
            val timePickerState = rememberTimePickerState(
                initialHour = hour,
                initialMinute = minute,
                is24Hour = true,
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                title = { Text(text = stringResource(R.string.settings_reminder_time_pick)) },
                text = { TimePicker(state = timePickerState) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            screenModel.setReminderTime(timePickerState.hour, timePickerState.minute)
                            showTimePicker = false
                        },
                    ) {
                        Text(text = stringResource(R.string.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text(text = stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun ReminderHint(
    text: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClick) {
            Text(text = action)
        }
    }
}

private fun appNotificationSettingsIntent(context: Context): Intent = Intent(
    Settings.ACTION_APP_NOTIFICATION_SETTINGS,
).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
