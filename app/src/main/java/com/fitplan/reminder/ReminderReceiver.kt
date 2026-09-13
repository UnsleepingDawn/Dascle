package com.fitplan.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fitplan.app.di.appGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 训练提醒闹钟到点时触发；查库是挂起操作，用 [goAsync] 把广播留到写通知之后再结束。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMINDER) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                context.appGraph.reminderScheduler.onAlarmFired()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** 开机或应用更新后闹钟会被清掉，这里按已保存的设置重新排上。 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                context.appGraph.reminderScheduler.sync()
        }
    }
}
