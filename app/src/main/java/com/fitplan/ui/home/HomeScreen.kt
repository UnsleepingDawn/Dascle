package com.fitplan.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.fitplan.presentation.util.Screen
import com.fitplan.presentation.util.Tab
import com.fitplan.ui.missed.MissedConfirmDialog
import com.fitplan.ui.missed.MissedDialogState
import com.fitplan.ui.missed.MissedHandleDialog
import com.fitplan.ui.missed.MissedTrainingScreenModel
import com.fitplan.ui.more.MoreTab
import com.fitplan.ui.plan.PlanTab
import com.fitplan.ui.stats.StatsTab
import com.fitplan.ui.today.TodayTab
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut

object HomeScreen : Screen() {

    private val TABS = listOf(
        TodayTab,
        PlanTab,
        StatsTab,
        MoreTab,
    )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        TabNavigator(
            tab = TodayTab,
            key = TabNavigatorKey,
        ) { tabNavigator ->
            // 让 Tab 内 push 的页面仍然挂在根 Navigator 上
            CompositionLocalProvider(LocalNavigator provides navigator) {
                val isTabletUi = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_UI_MIN_SCREEN_WIDTH_DP
                val navigationSuiteType = if (isTabletUi) {
                    NavigationSuiteType.NavigationRail
                } else {
                    NavigationSuiteType.NavigationBar
                }

                NavigationSuiteScaffold(
                    navigationSuiteType = navigationSuiteType,
                    navigationSuiteColors = NavigationSuiteDefaults.colors(
                        navigationRailContainerColor = MaterialTheme.colorScheme
                            .surfaceColorAtElevation(3.dp),
                    ),
                    navigationItemVerticalArrangement = Arrangement.Center,
                    navigationItems = {
                        TABS.fastForEach { NavigationSuiteTabItem(it, navigationSuiteType) }
                    },
                ) {
                    AnimatedContent(
                        targetState = tabNavigator.current,
                        transitionSpec = {
                            materialFadeThroughIn(
                                initialScale = 1f,
                                durationMillis = TabFadeDuration,
                            ) togetherWith materialFadeThroughOut(durationMillis = TabFadeDuration)
                        },
                        label = "tabContent",
                    ) { tab ->
                        tabNavigator.saveableState(key = "currentTab", tab) {
                            tab.Content()
                        }
                    }
                }
            }

            BackHandler(enabled = tabNavigator.current != TodayTab) {
                tabNavigator.current = TodayTab
            }
        }

        MissedTrainingPrompt()
    }

    /**
     * 打开 App 时先看看有没有「安排了训练却没练」的日子：有就弹窗问用户怎么处理。
     *
     * 放在 HomeScreen 而不是某个 Tab 里，是因为它是根页面，冷启动就进组合、回到前台也会重启，
     * 正好对应「每次打开 App 都检查一次」。
     */
    @Composable
    private fun MissedTrainingPrompt() {
        val screenModel = metroViewModel<MissedTrainingScreenModel>()
        val dialog by screenModel.dialog.collectAsState()
        // 从后台回到前台也要再查一次，所以跟着宿主 Activity 的生命周期走（冷启动另由 LaunchedEffect 兜底）。
        val lifecycle = (LocalActivity.current as? LifecycleOwner)?.lifecycle

        LaunchedEffect(Unit) { screenModel.check() }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) screenModel.check()
            }
            lifecycle?.addObserver(observer)
            onDispose { lifecycle?.removeObserver(observer) }
        }

        when (val state = dialog) {
            MissedDialogState.Hidden -> Unit

            is MissedDialogState.Confirm -> MissedConfirmDialog(
                missed = state.missed,
                onTrained = screenModel::confirmTrained,
                onNotTrained = { screenModel.askHowToHandle(state.missed) },
                onDismiss = screenModel::dismiss,
            )

            is MissedDialogState.Handle -> MissedHandleDialog(
                onPostpone = { screenModel.postpone(state.missed) },
                onSkip = { screenModel.skip(state.missed) },
                onDismiss = screenModel::dismiss,
            )
        }
    }

    @Composable
    private fun NavigationSuiteTabItem(
        tab: Tab,
        navigationSuiteType: NavigationSuiteType,
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationSuiteItem(
            navigationSuiteType = navigationSuiteType,
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = {
                Icon(
                    painter = tab.options.icon!!,
                    contentDescription = tab.options.title,
                )
            },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }

    @Suppress("ConstPropertyName")
    private const val TabFadeDuration = 200

    @Suppress("ConstPropertyName")
    private const val TabNavigatorKey = "HomeTabs"

    @Suppress("ConstPropertyName")
    private const val TABLET_UI_MIN_SCREEN_WIDTH_DP = 600
}
