package com.fitplan.domain.repository

import com.fitplan.domain.model.ExerciseProgressHint

/** 每个动作各自的渐进重量提示状态；没有记录表示「可以提醒」。 */
interface ExerciseProgressHintRepository {

    suspend fun get(exerciseId: Long): ExerciseProgressHint?

    /** 写入 / 覆盖该动作的状态；[ExerciseProgressHint.snoozeCount] 未变化时也照写。 */
    suspend fun upsert(hint: ExerciseProgressHint)

    /** 清空该动作的状态：恢复为可提醒，暂缓档位重新从 3 天开始。 */
    suspend fun clear(exerciseId: Long)
}
