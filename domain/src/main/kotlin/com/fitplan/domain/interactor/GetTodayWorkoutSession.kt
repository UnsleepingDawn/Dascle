package com.fitplan.domain.interactor

import com.fitplan.domain.model.WorkoutSession
import com.fitplan.domain.repository.WorkoutRepository
import dev.zacsweers.metro.Inject
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * 今天开始的那次训练（结束与否都算）；今天还没开练时返回 null。
 *
 * 「今天」以 `started_at` 落在哪个本地日期为准：昨天开练、跨零点才练完的那次仍算昨天，
 * 不会让「今天」凭空多出一次已经做完的训练。
 *
 * 今天既有已结束的训练、又残留一次未结束的训练时（重复点「开始训练」留下的孤儿），
 * 优先给已结束的那次：今日页据此进入完成态、不再给续练入口。孤儿只在今天之内出现——
 * 隔天的旧账由 [CloseStaleWorkouts] 收尾。
 */
@Inject
class GetTodayWorkoutSession(
    private val workoutRepository: WorkoutRepository,
) {

    suspend operator fun invoke(): WorkoutSession? {
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(zone).date
        val sessions = workoutRepository.getSessionsBetween(
            start = today.atStartOfDayIn(zone),
            end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
        )
        return sessions.filter { it.isFinished }.maxByOrNull { it.startedAt }
            ?: sessions.maxByOrNull { it.startedAt }
    }
}
