package com.fitplan.domain.interactor

import kotlinx.datetime.LocalDate

/**
 * 删除若干天后的日期映射：把 [removedDates] 从时间线上抽掉，[scheduledDates]（升序、每个训练日一条）
 * 里晚于它们的排期各自提前「被抽掉的日期个数」天。
 *
 * 被删的是训练日还是休息日都走这一套口径：被删日之后**整条时间线**（训练日与休息日一起）整体提前，
 * 练 / 休的节奏因此原样保留。日期只会往前走、不会互相撞上，末尾空出来的训练日自然变成休息日。
 *
 * 例：排期为 `10/12 A`、`10/14 B`、`10/16 C`（`10/13`、`10/15` 本来是休息日），
 * 删掉 `10/12` 时返回 `[(10/14 -> 10/13), (10/16 -> 10/15)]`；连同删掉 `10/12` 之后，
 * `10/12` 变休息、`10/13` 接着练 B、`10/14` 变休息、`10/15` 接着练 C、`10/16` 变休息。
 *
 * 被删的日期若本来就没有排期（休息日），它不会出现在 [scheduledDates] 里，映射不受影响。
 */
fun shiftBackAfterRemovedDates(
    scheduledDates: List<LocalDate>,
    removedDates: Set<LocalDate>,
): List<Pair<LocalDate, LocalDate>> =
    scheduledDates.mapNotNull { date ->
        val shift = removedDates.count { it < date }
        if (shift == 0) null else date to LocalDate.fromEpochDays(date.toEpochDays() - shift)
    }
