package com.fitplan.domain.repository

/**
 * 「低于计划目标」重填提醒的按动作标记。
 *
 * 只记「最近一次提醒时的 [versionCode]」：与当前版本一致才算本版本已提醒过，
 * 版本一变旧值自然失效，相当于每次更新都给所有动作重新开放一次提醒。
 */
interface ExerciseLowReminderRepository {

    /** 该动作在当前 [versionCode] 下是否已经提醒过。 */
    suspend fun isShown(exerciseId: Long, versionCode: Long): Boolean

    /** 记下该动作已给出过提醒（按当前 [versionCode]）。 */
    suspend fun markShown(exerciseId: Long, versionCode: Long)
}
