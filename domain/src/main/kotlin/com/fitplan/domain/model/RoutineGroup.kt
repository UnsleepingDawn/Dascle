package com.fitplan.domain.model

/**
 * 计划里的一个动作组：组里放若干动作，训练时从中挑最多 [maxPicks] 个来练。
 *
 * 组内动作各自保留自己的目标组数 / 次数 / 重量与组间休息，
 * 在记录页被选中后和单独排的动作一样独立记录。
 */
data class RoutineGroup(
    val id: Long,
    val routineId: Long,
    /** 顶层顺序，与单独排列的动作共用一套序号。 */
    val position: Int,
    /** 训练时最多从组里挑几个动作，恒 >= 1 且不超过组内动作数。 */
    val maxPicks: Int,
    /** 组名；为空表示没起过名字，界面按默认的「动作组」展示。 */
    val name: String? = null,
    /** 组内动作，按组内顺序排列。 */
    val exercises: List<RoutineExercise> = emptyList(),
)
