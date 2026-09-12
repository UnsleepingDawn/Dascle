package com.fitplan.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.fitplan.presentation.util.Screen
import com.fitplan.presentation.util.Tab
import com.fitplan.ui.more.MoreTab
import com.fitplan.ui.plan.PlanTab
import com.fitplan.ui.stats.StatsTab
import com.fitplan.ui.today.TodayTab
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
