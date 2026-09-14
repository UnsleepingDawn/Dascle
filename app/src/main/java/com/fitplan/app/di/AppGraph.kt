package com.fitplan.app.di

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.FileProvider
import com.fitplan.app.App
import com.fitplan.app.ui.main.MainActivity
import com.fitplan.core.metro.IsDebugBuild
import com.fitplan.data.Database
import com.fitplan.reminder.ReminderScheduler
import com.fitplan.reminder.RestNotifier
import com.fitplan.widget.WidgetManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlinx.serialization.json.Json

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [AppBindings::class],
)
interface AppGraph : ViewModelGraph {
    fun inject(app: App)
    fun inject(mainActivity: MainActivity)

    val context: Context

    @get:IsDebugBuild
    val isDebugBuild: Boolean

    val viewModelFactory: MetroViewModelFactory

    /** 「今日训练」桌面组件的刷新入口。 */
    val widgetManager: WidgetManager

    /** 训练提醒的闹钟与通知入口，供 Receiver 与设置页使用。 */
    val reminderScheduler: ReminderScheduler

    /** 组间休息的后台倒计时通知，供记录页与到点闹钟使用。 */
    val restNotifier: RestNotifier

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context, @Provides @IsDebugBuild isDebugBuild: Boolean): AppGraph
    }
}

@BindingContainer
object AppBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun providesSqlDriver(context: Context): SqlDriver = AndroidxSqliteDriver(
        driver = BundledSQLiteDriver(),
        databaseType = AndroidxSqliteDatabaseType.FileProvider(context, DATABASE_NAME),
        schema = Database.Schema,
        configuration = AndroidxSqliteConfiguration(
            isForeignKeyConstraintsEnabled = true,
        ),
    )

    @Provides
    @SingleIn(AppScope::class)
    fun providesDatabase(driver: SqlDriver): Database = Database(driver)

    @Provides
    @SingleIn(AppScope::class)
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private const val DATABASE_NAME = "fitplan.db"
}
