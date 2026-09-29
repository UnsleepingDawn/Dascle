package com.fitplan.domain.interactor

import com.fitplan.domain.repository.WorkoutRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 收尾隔天还挂着的未完成训练：用户忘了点「结束训练」就退出，这条记录会永远留在
 * `finished_at IS NULL` 里，把今日页顶成「继续训练」并在隔天继续显示「未完成的训练」。
 *
 * 今天开始的训练不算旧账——那正是「继续训练」要照顾的场景；只有 `started_at` 落在今天之前、
 * 且**已经勾过至少一组**的，才按它的开始时刻补记为已结束：那次训练的成果照常出现在训练日历与统计里
 * （时长为 0，统计页本来就不显示 0 时长），只是不再有续练入口。
 *
 * 一组都没勾过的（重复点「开始训练」留下的孤儿）不写库，交给今日页按「只看当天」的口径隐藏，
 * 免得凭空在日历上多出一次没练过的训练。这一操作幂等：并发执行时算出的 `finished_at` 相同。
 */
@Inject
class CloseStaleWorkouts(
    private val workoutRepository: WorkoutRepository,
) {

    suspend operator fun invoke() {
        val zone = TimeZone.currentSystemDefault()
        val todayStart = Clock.System.now().toLocalDateTime(zone).date.atStartOfDayIn(zone)
        workoutRepository.getUnfinishedSessions()
            .filter { it.startedAt < todayStart }
            .forEach { session ->
                val hasCompletedSet = workoutRepository.getSets(session.id).any { it.completed }
                if (hasCompletedSet) workoutRepository.finishSession(session.id, session.startedAt)
            }
    }
}
