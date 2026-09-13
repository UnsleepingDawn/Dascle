package com.fitplan.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fitplan.app.R
import com.fitplan.app.ui.main.MainActivity
import com.fitplan.core.common.preference.Preference
import com.fitplan.core.common.preference.PreferenceStore
import com.fitplan.domain.interactor.GetScheduledRoutinesForDate
import com.fitplan.domain.repository.WorkoutRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 训练提醒：每天在用户设定的时间响一次闹钟，到点时查一下当天有没有排期、是不是已经练过，
 * 需要才发通知，然后把第二天的闹钟排上。
 *
 * 降级策略：
 * - Android 12+ 需要 `SCHEDULE_EXACT_ALARM` 授权才能用精确闹钟；没有授权时退回
 *   [AlarmManager.setAndAllowWhileIdle]（可能晚几分钟），提醒不会丢。
 * - Android 13+ 需要 `POST_NOTIFICATIONS` 运行时权限；没有授权时不发通知。
 */
@Inject
@SingleIn(AppScope::class)
class ReminderScheduler(
    private val context: Context,
    private val getScheduledRoutinesForDate: GetScheduledRoutinesForDate,
    private val workoutRepository: WorkoutRepository,
    preferenceStore: PreferenceStore,
) {

    val enabled: Preference<Boolean> = preferenceStore.getBoolean(KEY_ENABLED, false)

    val hour: Preference<Int> = preferenceStore.getInt(KEY_HOUR, DEFAULT_HOUR)

    val minute: Preference<Int> = preferenceStore.getInt(KEY_MINUTE, DEFAULT_MINUTE)

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** 设置变化后调用：开启就排下一次闹钟，关闭就取消。 */
    fun sync() {
        if (enabled.get()) {
            ensureChannel()
            scheduleNext()
        } else {
            cancel()
        }
    }

    /** 精确闹钟授权（Android 12+）。低版本系统没有这个限制，始终可用。 */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** 通知是否可用；Android 13+ 未授予 `POST_NOTIFICATIONS` 时为 false。 */
    fun canPostNotifications(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 闹钟到点：满足条件就提醒，然后排下一天。 */
    suspend fun onAlarmFired() {
        if (!enabled.get()) {
            cancel()
            return
        }

        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val date = now.toLocalDateTime(zone).date
        val routines = getScheduledRoutinesForDate(date)
        val trainedToday = workoutRepository
            .getCompletedSetsBetween(date.atStartOfDayIn(zone), now)
            .isNotEmpty()

        if (routines.isNotEmpty() && !trainedToday && canPostNotifications()) {
            notifyTodayTraining(routines.map { it.routine.name })
        }

        // 闹钟是一次性的，响过之后补上下一天。
        scheduleNext()
    }

    private fun scheduleNext() {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val nowLocal = now.toLocalDateTime(zone)
        val targetHour = hour.get()
        val targetMinute = minute.get()

        val passedToday = nowLocal.hour > targetHour ||
            (nowLocal.hour == targetHour && nowLocal.minute >= targetMinute)
        val date = if (passedToday) nowLocal.date.plus(1, DateTimeUnit.DAY) else nowLocal.date
        val triggerAt = LocalDateTime(date, LocalTime(targetHour, targetMinute))
            .toInstant(zone)
            .toEpochMilliseconds()

        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent())
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent())
        }
    }

    private fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            },
        )
    }

    @SuppressLint("MissingPermission")
    private fun notifyTodayTraining(routineNames: List<String>) {
        ensureChannel()
        val body = context.getString(R.string.reminder_notification_text, routineNames.joinToString("、"))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE_ALARM,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMINDER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE_CONTENT,
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_REMINDER = "com.fitplan.app.action.WORKOUT_REMINDER"

        private const val CHANNEL_ID = "workout_reminder"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE_ALARM = 2001
        private const val REQUEST_CODE_CONTENT = 2002

        private const val KEY_ENABLED = "reminder_enabled"
        private const val KEY_HOUR = "reminder_hour"
        private const val KEY_MINUTE = "reminder_minute"

        /** 默认晚上 7 点，多数人下班后的训练时间。 */
        private const val DEFAULT_HOUR = 19
        private const val DEFAULT_MINUTE = 0
    }
}
