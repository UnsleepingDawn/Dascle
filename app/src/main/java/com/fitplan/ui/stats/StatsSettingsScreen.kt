package com.fitplan.ui.stats

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fitplan.app.R
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Screen

/**
 * 统计设置页：从统计页右上角的齿轮进入，集中放与统计口径有关的开关。
 *
 * 目前还没有可以调整的项，先把入口与页面留出来，后续的统计设置加在这里。
 */
object StatsSettingsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.stats_settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = navigator::pop) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            EmptyScreen(
                message = stringResource(R.string.stats_settings_empty),
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}
