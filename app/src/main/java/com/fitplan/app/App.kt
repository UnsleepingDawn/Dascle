package com.fitplan.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.fitplan.app.di.AppGraph
import com.fitplan.core.metro.GraphProvider
import com.fitplan.domain.interactor.CloseStaleWorkouts
import com.fitplan.domain.repository.ExerciseSeedRepository
import com.fitplan.domain.repository.RoutineRepository
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class App : Application(), GraphProvider<AppGraph> {

    override val graph: AppGraph by lazy {
        createGraphFactory<AppGraph.Factory>().create(context = this, isDebugBuild = BuildConfig.DEBUG)
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject lateinit var appContext: Context

    @Inject lateinit var exerciseSeedRepository: ExerciseSeedRepository

    @Inject lateinit var closeStaleWorkouts: CloseStaleWorkouts

    @Inject lateinit var routineRepository: RoutineRepository

    override fun onCreate() {
        super<Application>.onCreate()

        graph.inject(this)

        // 每次启动按已保存的设置把训练提醒闹钟排上（闹钟在重启/更新后会被系统清掉）。
        graph.reminderScheduler.sync()

        Log.d(TAG, "AppGraph initialized: $graph, appContext injected: ${::appContext.isInitialized}")

        applicationScope.launch {
            // 先把隔天还挂着的未完成训练收尾，用户直接打开「计划」日历也能看到一致的结果。
            closeStaleWorkouts()
            // 临时计划是日历休息日开出来的一次性编排：上次没加动作就退出的空壳在这里收掉。
            routineRepository.deleteUnusedTempPlans()
            val inserted = exerciseSeedRepository.importSeedExercises()
            Log.d(TAG, "Seed exercises inserted: $inserted")
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        Log.d(TAG, "App terminated")
        super.onTerminate()
    }

    companion object {
        private const val TAG = "FitPlan"
    }
}
