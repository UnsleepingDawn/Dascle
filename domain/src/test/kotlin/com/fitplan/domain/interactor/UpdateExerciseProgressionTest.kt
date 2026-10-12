package com.fitplan.domain.interactor

import com.fitplan.domain.model.Equipment
import com.fitplan.domain.model.Exercise
import com.fitplan.domain.model.ExerciseLoadMode
import com.fitplan.domain.model.ExerciseMetric
import com.fitplan.domain.model.ExerciseProgressHint
import com.fitplan.domain.model.MuscleGroup
import com.fitplan.domain.model.Routine
import com.fitplan.domain.model.RoutineExercise
import com.fitplan.domain.model.RoutineItem
import com.fitplan.domain.repository.ExerciseProgressHintRepository
import com.fitplan.domain.repository.ExerciseRepository
import com.fitplan.domain.repository.RoutineRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * 盯住「渐进加量」的写入范围：确认提示弹窗确认后，新目标既写进动作库默认值
 * （决定「以后新加入计划」的预填值），也写进所有已存在计划的目标值，并且清掉提示状态。
 *
 * 「所有计划」的语义由 [RoutineRepository.updateTargetWeightForExercise] 等方法的实现保证，
 * 这里只断言交互类确实调用了它们，且没有漏掉动作库或提示状态。
 */
class UpdateExerciseProgressionTest {

    @Test
    fun `提高重量同时写动作库默认值与计划目标并清空提示状态`() = runTest {
        val hint = hints()
        val exerciseRepo = FakeExerciseRepository(weightedExercise())
        val routineRepo = FakeRoutineRepository()
        val progression = UpdateExerciseProgression(exerciseRepo, routineRepo, hint)

        progression.applyWeight(exerciseId = 1L, newWeight = 42.5)

        exerciseRepo.defaultWeights shouldBe mapOf(1L to 42.5)
        routineRepo.weightUpdates shouldBe listOf(1L to 42.5)
        hint.cleared shouldBe listOf(1L)
    }

    @Test
    fun `增加次数同时写动作库默认次数与计划目标并清空提示状态`() = runTest {
        val hint = hints()
        val exerciseRepo = FakeExerciseRepository(weightedExercise())
        val routineRepo = FakeRoutineRepository()
        val progression = UpdateExerciseProgression(exerciseRepo, routineRepo, hint)

        progression.applyReps(exerciseId = 1L, newReps = 15)

        exerciseRepo.defaultReps shouldBe mapOf(1L to 15)
        routineRepo.repsUpdates shouldBe listOf(1L to 15)
        hint.cleared shouldBe listOf(1L)
    }

    @Test
    fun `增加时长同时写动作库默认时长与计划目标并清空提示状态`() = runTest {
        val hint = hints()
        val exerciseRepo = FakeExerciseRepository(weightedExercise())
        val routineRepo = FakeRoutineRepository()
        val progression = UpdateExerciseProgression(exerciseRepo, routineRepo, hint)

        progression.applySeconds(exerciseId = 1L, newSeconds = 90)

        exerciseRepo.defaultDurations shouldBe mapOf(1L to 90)
        routineRepo.secondsUpdates shouldBe listOf(1L to 90)
        hint.cleared shouldBe listOf(1L)
    }

    @Test
    fun `改目标组数与组间休息只写计划不碰动作库默认值`() = runTest {
        val hint = hints()
        val exerciseRepo = FakeExerciseRepository(weightedExercise())
        val routineRepo = FakeRoutineRepository()
        val progression = UpdateExerciseProgression(exerciseRepo, routineRepo, hint)

        progression.applySets(exerciseId = 1L, newSets = 5)
        progression.applyRest(exerciseId = 1L, newRestSeconds = 120)

        // 动作库没有「默认组数 / 默认休息」这两列，只能落在计划上。
        routineRepo.setsUpdates shouldBe listOf(1L to 5)
        routineRepo.restUpdates shouldBe listOf(1L to 120)
        exerciseRepo.defaultWeights shouldBe emptyMap()
        exerciseRepo.defaultReps shouldBe emptyMap()
        exerciseRepo.defaultDurations shouldBe emptyMap()
        hint.cleared shouldBe listOf(1L, 1L)
    }

    @Test
    fun `手动把重量调重才算变难并清空提示状态`() = runTest {
        val hint = hints()
        val progression = UpdateExerciseProgression(
            FakeExerciseRepository(weightedExercise()),
            FakeRoutineRepository(),
            hint,
        )

        progression.onManualWeightChanged(
            exerciseId = 1L,
            previousWeight = 30.0,
            newWeight = 35.0,
            loadMode = ExerciseLoadMode.EXTERNAL,
        )

        hint.cleared shouldBe listOf(1L)
    }

    @Test
    fun `手动把重量调轻或持平不动提示状态`() = runTest {
        val hint = hints()
        val progression = UpdateExerciseProgression(
            FakeExerciseRepository(weightedExercise()),
            FakeRoutineRepository(),
            hint,
        )

        progression.onManualWeightChanged(
            1L,
            previousWeight = 30.0,
            newWeight = 25.0,
            loadMode = ExerciseLoadMode.EXTERNAL,
        )
        progression.onManualWeightChanged(
            1L,
            previousWeight = 30.0,
            newWeight = 30.0,
            loadMode = ExerciseLoadMode.EXTERNAL,
        )

        hint.cleared shouldBe emptyList()
    }

    @Test
    fun `辅助动作把助力调低才算变难`() = runTest {
        val hint = hints()
        val progression = UpdateExerciseProgression(
            FakeExerciseRepository(weightedExercise(loadMode = ExerciseLoadMode.ASSISTED)),
            FakeRoutineRepository(),
            hint,
        )

        // 助力从 30 降到 25 更难；升高反而更轻松，不该重新打开提醒。
        progression.onManualWeightChanged(
            1L,
            previousWeight = 30.0,
            newWeight = 25.0,
            loadMode = ExerciseLoadMode.ASSISTED,
        )
        progression.onManualWeightChanged(
            1L,
            previousWeight = 30.0,
            newWeight = 35.0,
            loadMode = ExerciseLoadMode.ASSISTED,
        )

        hint.cleared shouldBe listOf(1L)
    }

    @Test
    fun `没给改前重量时用动作库默认重量当基准`() = runTest {
        val hint = hints()
        val progression = UpdateExerciseProgression(
            FakeExerciseRepository(weightedExercise(defaultWeight = 30.0)),
            FakeRoutineRepository(),
            hint,
        )

        progression.onManualWeightChanged(
            1L,
            previousWeight = null,
            newWeight = 31.0,
            loadMode = ExerciseLoadMode.EXTERNAL,
        )
        progression.onManualWeightChanged(
            1L,
            previousWeight = null,
            newWeight = 29.0,
            loadMode = ExerciseLoadMode.EXTERNAL,
        )

        hint.cleared shouldBe listOf(1L)
    }

    @Test
    fun `没有新重量时直接返回，不做任何写入`() = runTest {
        val hint = hints()
        val exerciseRepo = FakeExerciseRepository(weightedExercise())
        val progression = UpdateExerciseProgression(exerciseRepo, FakeRoutineRepository(), hint)

        progression.onManualWeightChanged(
            1L,
            previousWeight = 30.0,
            newWeight = null,
            loadMode = ExerciseLoadMode.EXTERNAL,
        )

        hint.cleared shouldBe emptyList()
    }
}

private fun weightedExercise(
    defaultWeight: Double? = 20.0,
    loadMode: ExerciseLoadMode = ExerciseLoadMode.EXTERNAL,
): Exercise = Exercise(
    id = 1L,
    name = "杠铃卧推",
    muscleGroup = MuscleGroup.CHEST,
    equipment = Equipment.BARBELL,
    description = "",
    isCustom = false,
    createdAt = Instant.parse("2026-09-01T00:00:00Z"),
    loadMode = loadMode,
    defaultWeight = defaultWeight,
)

private fun hints(): FakeHintRepository = FakeHintRepository()

private class FakeHintRepository : ExerciseProgressHintRepository {

    val cleared = mutableListOf<Long>()

    override suspend fun get(exerciseId: Long): ExerciseProgressHint? = null

    override suspend fun upsert(hint: ExerciseProgressHint) = error("本测试不涉及写入提示状态")

    override suspend fun clear(exerciseId: Long) {
        cleared += exerciseId
    }
}

private class FakeExerciseRepository(
    private val exercise: Exercise?,
) : ExerciseRepository {

    val defaultWeights = mutableMapOf<Long, Double>()
    val defaultReps = mutableMapOf<Long, Int>()
    val defaultDurations = mutableMapOf<Long, Int>()

    override suspend fun getAll(): List<Exercise> = listOfNotNull(exercise)

    override suspend fun getById(id: Long): Exercise? = exercise?.takeIf { it.id == id }

    override suspend fun getByIds(ids: List<Long>): List<Exercise> = listOfNotNull(exercise).filter { it.id in ids }

    override suspend fun getByMuscleGroup(muscleGroup: MuscleGroup): List<Exercise> = emptyList()

    override suspend fun getCustom(): List<Exercise> = emptyList()

    override suspend fun getNonCustomNames(): Set<String> = emptySet()

    override suspend fun count(): Long = listOfNotNull(exercise).size.toLong()

    override suspend fun insert(
        name: String,
        muscleGroup: MuscleGroup,
        secondaryMuscleGroups: List<MuscleGroup>,
        equipment: Equipment,
        description: String,
        isCustom: Boolean,
        createdAt: Instant,
        metric: ExerciseMetric,
        loadMode: ExerciseLoadMode,
    ): Long = error("本测试不涉及新增动作")

    override suspend fun update(exercise: Exercise) = error("本测试不涉及更新动作")

    override suspend fun updateDefaultWeight(id: Long, weight: Double) {
        defaultWeights[id] = weight
    }

    override suspend fun updateDefaultReps(id: Long, reps: Int) {
        defaultReps[id] = reps
    }

    override suspend fun updateDefaultDuration(id: Long, seconds: Int) {
        defaultDurations[id] = seconds
    }

    override suspend fun deleteById(id: Long) = error("本测试不涉及删除动作")
}

private class FakeRoutineRepository : RoutineRepository {

    val weightUpdates = mutableListOf<Pair<Long, Double>>()
    val repsUpdates = mutableListOf<Pair<Long, Int>>()
    val secondsUpdates = mutableListOf<Pair<Long, Int>>()
    val setsUpdates = mutableListOf<Pair<Long, Int>>()
    val restUpdates = mutableListOf<Pair<Long, Int>>()

    override suspend fun getAll(): List<Routine> = error("本测试不涉及读取计划")

    override suspend fun getPermanent(): List<Routine> = error("本测试不涉及读取计划")

    override suspend fun getById(id: Long): Routine? = error("本测试不涉及读取计划")

    override suspend fun count(): Long = error("本测试不涉及读取计划")

    override suspend fun insert(name: String, createdAt: Instant): Long = error("本测试不涉及新增计划")

    override suspend fun insertTemp(name: String, createdAt: Instant): Long = error("本测试不涉及新增计划")

    override suspend fun deleteUnusedTempPlans() = error("本测试不涉及清理计划")

    override suspend fun update(id: Long, name: String) = error("本测试不涉及更新计划")

    override suspend fun deleteById(id: Long) = error("本测试不涉及删除计划")

    override suspend fun getExercises(routineId: Long): List<RoutineExercise> = error("本测试不涉及读取编排")

    override suspend fun getItems(routineId: Long): List<RoutineItem> = error("本测试不涉及读取编排")

    override suspend fun addExercise(
        routineId: Long,
        exerciseId: Long,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        targetWeight: Double?,
        targetSeconds: Int?,
    ): Long = error("本测试不涉及新增编排")

    override suspend fun updateExerciseTargets(
        id: Long,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        targetWeight: Double?,
        targetSeconds: Int?,
    ) = error("本测试不涉及修改编排")

    override suspend fun reorderItems(routineId: Long, orderedItems: List<RoutineItem>) =
        error("本测试不涉及排序")

    override suspend fun addGroup(routineId: Long, maxPicks: Int, name: String?): Long =
        error("本测试不涉及新增动作组")

    override suspend fun updateGroupMaxPicks(groupId: Long, maxPicks: Int) = error("本测试不涉及修改动作组")

    override suspend fun updateGroupName(groupId: Long, name: String?) = error("本测试不涉及修改动作组")

    override suspend fun removeGroup(groupId: Long) = error("本测试不涉及删除动作组")

    override suspend fun addExerciseToGroup(
        groupId: Long,
        exerciseId: Long,
        targetSets: Int,
        targetReps: Int,
        restSeconds: Int,
        targetWeight: Double?,
        targetSeconds: Int?,
    ) = error("本测试不涉及新增编排")

    override suspend fun updateTargetWeightForExercise(exerciseId: Long, weight: Double) {
        weightUpdates += exerciseId to weight
    }

    override suspend fun updateTargetRepsForExercise(exerciseId: Long, reps: Int) {
        repsUpdates += exerciseId to reps
    }

    override suspend fun updateTargetSecondsForExercise(exerciseId: Long, seconds: Int) {
        secondsUpdates += exerciseId to seconds
    }

    override suspend fun updateTargetSetsForExercise(exerciseId: Long, sets: Int) {
        setsUpdates += exerciseId to sets
    }

    override suspend fun updateRestSecondsForExercise(exerciseId: Long, seconds: Int) {
        restUpdates += exerciseId to seconds
    }

    override suspend fun removeExercise(id: Long) = error("本测试不涉及删除编排")
}
