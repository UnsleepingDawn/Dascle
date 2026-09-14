package com.fitplan.domain.model

import kotlin.time.Instant

data class Exercise(
    val id: Long,
    val name: String,
    /** 主要刺激的部位，[muscleGroups] 里的第一个。 */
    val muscleGroup: MuscleGroup,
    /** 次要刺激的部位，不重复 [muscleGroup]。 */
    val secondaryMuscleGroups: List<MuscleGroup> = emptyList(),
    val equipment: Equipment,
    val description: String,
    val isCustom: Boolean,
    val createdAt: Instant,
    /** 内置动作的默认重量（kg）；null 表示自重或种子没有标注。 */
    val defaultWeight: Double? = null,
    /** 内置动作的默认时长（秒）；非 null 表示该动作按计时录入。 */
    val defaultDurationSeconds: Int? = null,
) {
    /** 该动作涉及的全部部位，主部位在前、次部位去重在后。 */
    val muscleGroups: List<MuscleGroup>
        get() = (listOf(muscleGroup) + secondaryMuscleGroups).distinct()

    /** 种子把该动作标成了计时类。 */
    val isTimed: Boolean get() = defaultDurationSeconds != null
}
