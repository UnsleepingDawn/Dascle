package com.fitplan.domain.model

/**
 * 计划编辑页顶层的「一项」：要么是一个单独排列的动作，要么是一个动作组。
 *
 * 两者的 [position] 共用同一套序号，因此列表里动作与动作组可以任意交错排列。
 */
sealed interface RoutineItem {

    val position: Int

    /** 这一项里的动作：单独排的动作就是它自己，动作组则是组内全部动作。 */
    val exercises: List<RoutineExercise>
        get() = when (this) {
            is Exercise -> listOf(value)
            is Group -> value.exercises
        }

    data class Exercise(val value: RoutineExercise) : RoutineItem {
        override val position: Int get() = value.position
    }

    data class Group(val value: RoutineGroup) : RoutineItem {
        override val position: Int get() = value.position
    }
}
