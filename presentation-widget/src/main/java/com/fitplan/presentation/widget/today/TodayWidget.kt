package com.fitplan.presentation.widget.today

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/** 桌面组件要展示的内容；文案由 app 侧拼好，这里只负责画。 */
data class TodayWidgetState(
    val dateText: String,
    val statusText: String,
    /** 今日计划，一行一个；条数已由调用方按组件高度裁好。 */
    val routineLines: List<String>,
    /** 点组件要打开的页面；为 null 时组件不可点。 */
    val launchIntent: Intent? = null,
)

/**
 * 「今日训练」桌面组件。取数统一走 [loadState]：主动刷新（app 侧 WidgetManager）与
 * 系统定时刷新拿到的是同一份内容，组件本身不认识数据库。
 */
class TodayWidget(
    private val loadState: suspend (Context) -> TodayWidgetState,
) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        provideContent { TodayWidgetContent(state) }
    }
}

@Composable
private fun TodayWidgetContent(state: TodayWidgetState) {
    val clickModifier = state.launchIntent
        ?.let { intent -> GlanceModifier.clickable(actionStartActivity(intent)) }
        ?: GlanceModifier

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .then(clickModifier)
            .padding(WIDGET_PADDING),
    ) {
        Text(
            text = state.dateText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = state.statusText,
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp),
            maxLines = 1,
        )
        if (state.routineLines.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
        }
        state.routineLines.forEach { line ->
            Text(
                text = line,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                maxLines = 1,
            )
        }
    }
}

private val WIDGET_PADDING = 12.dp
