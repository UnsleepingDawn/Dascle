package com.fitplan.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.fitplan.app.di.appGraph
import com.fitplan.presentation.widget.today.TodayWidget

/**
 * 系统来要内容（第一次添加组件、定时刷新、重启后重画）时现取一份今日数据；
 * 取数与主动刷新共用 [WidgetManager.todayState]。
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TodayWidget { context ->
        context.appGraph.widgetManager.todayState()
    }
}
