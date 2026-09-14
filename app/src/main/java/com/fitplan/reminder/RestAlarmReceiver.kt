package com.fitplan.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fitplan.app.di.appGraph

/** 组间休息到点的精确闹钟：把通知栏的倒计时换成「休息结束」。 */
class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RestNotifier.ACTION_REST_END) return
        context.appGraph.restNotifier.onEndFired(intent.getStringExtra(RestNotifier.EXTRA_EXERCISE).orEmpty())
    }
}
