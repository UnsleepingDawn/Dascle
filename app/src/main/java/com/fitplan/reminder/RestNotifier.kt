package com.fitplan.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.fitplan.app.R
import com.fitplan.app.ui.main.MainActivity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 组间休息的后台通知：退到应用后台时挂一条带系统倒计时的通知——倒计时由系统自己渲染，
 * 不依赖进程存活，所以锁屏、被系统冻结都不会停；到点再用精确闹钟把通知改成「休息结束」并震动。
 *
 * 回到前台或跳过休息时会撤掉通知，所以「前台看记录页、后台看通知栏」不会重复提醒。
 */
@Inject
@SingleIn(AppScope::class)
class RestNotifier(
    private val context: Context,
) : DefaultLifecycleObserver {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** 当前这次休息的动作名与结束时刻；不在休息时为 null。 */
    private var exerciseName: String = ""
    private var endAt: Instant? = null

    init {
        // 休息只可能在应用内开始，盯着前后台切换即可。
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /** 开始一次组间休息；正在前台时不打扰，等退到后台再挂通知。 */
    fun start(exerciseName: String, endAt: Instant) {
        this.exerciseName = exerciseName
        this.endAt = endAt
        if (!isInForeground()) notifyRest(exerciseName, endAt)
    }

    /** 跳过休息、结束或放弃训练：撤掉通知与到点闹钟。 */
    fun cancel() {
        endAt = null
        hideNotification()
    }

    override fun onStart(owner: LifecycleOwner) {
        // 回到应用内就不需要通知了；到点闹钟一并撤掉，避免退出时重复震动。
        hideNotification()
    }

    override fun onStop(owner: LifecycleOwner) {
        val deadline = endAt ?: return
        if (deadline > Clock.System.now()) notifyRest(exerciseName, deadline)
    }

    /** 到点闹钟触发：把通知栏的倒计时换成「休息结束」，并震动一次。 */
    @SuppressLint("MissingPermission")
    fun onEndFired(exerciseName: String) {
        val name = exerciseName.ifBlank { this.exerciseName }
        endAt = null
        if (!canPostNotifications()) return
        ensureChannel()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, buildEndNotification(name).build())
        vibrate()
    }

    @SuppressLint("MissingPermission")
    private fun notifyRest(exerciseName: String, endAt: Instant) {
        if (!canPostNotifications()) return
        ensureChannel()
        NotificationManagerCompat.from(
            context,
        ).notify(NOTIFICATION_ID, buildRestNotification(exerciseName, endAt).build())
        scheduleEndAlarm(endAt)
    }

    private fun hideNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        alarmManager.cancel(endPendingIntent())
    }

    private fun isInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun buildRestNotification(exerciseName: String, endAt: Instant): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.workout_rest))
            .setContentText(context.getString(R.string.workout_rest_notification_text, exerciseName))
            .setWhen(endAt.toEpochMilliseconds())
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    private fun buildEndNotification(exerciseName: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.workout_rest_done))
            .setContentText(context.getString(R.string.workout_rest_notification_done, exerciseName))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)

    private fun scheduleEndAlarm(endAt: Instant) {
        val triggerAt = endAt.toEpochMilliseconds()
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, endPendingIntent())
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, endPendingIntent())
        }
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** 通知是否可用；Android 13+ 未授予 `POST_NOTIFICATIONS` 时为 false。 */
    private fun canPostNotifications(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.workout_rest_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.workout_rest_channel_description)
            },
        )
    }

    private fun endPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE_END,
        Intent(context, RestAlarmReceiver::class.java)
            .setAction(ACTION_REST_END)
            .putExtra(EXTRA_EXERCISE, exerciseName),
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

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    companion object {
        const val ACTION_REST_END = "com.fitplan.app.action.REST_END"
        const val EXTRA_EXERCISE = "exercise_name"

        private const val CHANNEL_ID = "workout_rest"
        private const val NOTIFICATION_ID = 1002
        private const val REQUEST_CODE_END = 3001
        private const val REQUEST_CODE_CONTENT = 3002
        private const val VIBRATION_MILLIS = 400L
    }
}
