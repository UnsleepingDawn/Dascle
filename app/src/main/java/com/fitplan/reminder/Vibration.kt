package com.fitplan.reminder

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 短震一次。组间休息到点、倒数器到点、计时弹窗的 3-2-1 都用它。
 *
 * `VIBRATE` 是安装即授予的普通权限，调用前不必再判断；Android 12 起振动器由
 * `VibratorManager` 统一管理，取不到时退回旧的 `Vibrator`。
 */
@SuppressLint("MissingPermission")
fun vibrateOnce(context: Context, durationMillis: Long = DEFAULT_VIBRATION_MILLIS) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    vibrator?.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
}

/** 默认震动时长（毫秒）。 */
private const val DEFAULT_VIBRATION_MILLIS = 400L
