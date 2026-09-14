package com.fitplan.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.fitplan.app.R
import com.fitplan.app.ui.main.MainActivity
import com.fitplan.domain.interactor.GetScheduledRoutinesForDate
import com.fitplan.domain.interactor.ScheduledRoutine
import com.fitplan.domain.model.WorkoutSet
import com.fitplan.domain.repository.WorkoutRepository
import com.fitplan.presentation.widget.today.TodayWidget
import com.fitplan.presentation.widget.today.TodayWidgetState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 「今日训练」桌面组件的取数与刷新。训练或排期一变，相关 ScreenModel 就调 [updateTodayWidget]
 * 主动重画；系统定时刷新与重新添加组件则直接走 [todayState]，两条路取的是同一份数据。
 */
@Inject
@SingleIn(AppScope::class)
class WidgetManager(
    private val context: Context,
    private val getScheduledRoutinesForDate: GetScheduledRoutinesForDate,
    private val workoutRepository: WorkoutRepository,
) {

    /** 数据变了就把组件重画一遍。 */
    suspend fun updateTodayWidget() {
        TodayWidget { todayState() }.updateAll(context)
    }

    /** 组装今日状态；组件渲染时由 Receiver 调到这里。 */
    suspend fun todayState(): TodayWidgetState {
        val timeZone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val date = now.toLocalDateTime(timeZone).date
        val dayStart = date.atStartOfDayIn(timeZone)
        val routines = getScheduledRoutinesForDate(date)

        // 只有在今天开练的那场未完成训练才算「训练中」，隔天剩下的旧账不往组件上摆。
        val inProgressSets = workoutRepository.getUnfinishedSessions()
            .maxByOrNull { it.startedAt }
            ?.takeIf { it.startedAt >= dayStart }
            ?.let { session -> workoutRepository.getSets(session.id).count { it.completed } }
        val finishedSets = workoutRepository.getCompletedSetsBetween(dayStart, now)

        return TodayWidgetState(
            dateText = dateText(date),
            statusText = statusText(routines.isNotEmpty(), inProgressSets, finishedSets),
            routineLines = routineLines(routines),
            launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
    }

    private fun dateText(date: LocalDate): String {
        val weekdayNames = context.resources.getStringArray(R.array.weekday_names)
        return context.getString(
            R.string.today_date,
            date.month.ordinal + 1,
            date.day,
            weekdayNames[date.dayOfWeek.ordinal],
        )
    }

    /** 正在练 > 今天已经练完 > 有计划还没开始 > 今天休息。 */
    private fun statusText(
        hasRoutine: Boolean,
        inProgressSets: Int?,
        finishedSets: List<WorkoutSet>,
    ): String = when {
        inProgressSets != null -> context.getString(R.string.widget_status_in_progress, inProgressSets)

        finishedSets.isNotEmpty() -> context.getString(
            R.string.widget_status_finished,
            finishedSets.size,
        )

        hasRoutine -> context.getString(R.string.widget_status_ready)
        else -> context.getString(R.string.widget_status_rest)
    }

    private fun routineLines(routines: List<ScheduledRoutine>): List<String> {
        val visible = routines.take(MAX_ROUTINE_LINES)
        val lines = visible.map { scheduled ->
            context.getString(R.string.widget_routine_line, scheduled.routine.name, scheduled.exercises.size)
        }
        return if (routines.size > visible.size) {
            lines + context.getString(R.string.widget_more_routines, routines.size)
        } else {
            lines
        }
    }

    private companion object {
        /** 组件高度有限，排期再多也只摆前几个，剩下的折成一行「共 N 个计划」。 */
        const val MAX_ROUTINE_LINES = 3
    }
}
