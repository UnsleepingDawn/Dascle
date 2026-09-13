package com.fitplan.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.fitplan.app.R
import com.fitplan.presentation.core.components.IconItem
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.util.Tab
import com.fitplan.ui.settings.SettingsScreen

object MoreTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 3u,
            title = stringResource(R.string.tab_more),
            icon = rememberVectorPainter(Icons.Filled.Person),
        )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = { scrollBehavior ->
                TopAppBar(
                    title = { Text(text = stringResource(R.string.tab_more)) },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(modifier = Modifier.padding(contentPadding)) {
                IconItem(
                    label = stringResource(R.string.settings_title),
                    icon = Icons.Filled.Settings,
                    onClick = { navigator.push(SettingsScreen) },
                )
            }
        }
    }
}
