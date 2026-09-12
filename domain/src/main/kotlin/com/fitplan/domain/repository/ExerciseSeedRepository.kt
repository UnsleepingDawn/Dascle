package com.fitplan.domain.repository

/** 动作库种子数据的导入入口。 */
interface ExerciseSeedRepository {

    /**
     * 按计划 7.2 的流程导入种子动作：版本号与 `seed_meta` 不一致时才执行，
     * 以 `name` 去重，只插入缺失的内置动作，不覆盖用户已修改的记录。
     *
     * @return 本次新插入的动作条数。
     */
    suspend fun importSeedExercises(): Int
}
