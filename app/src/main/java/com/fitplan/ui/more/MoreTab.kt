package com.fitplan.ui.more

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Tab

object MoreTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 3u,
            title = "我的",
            icon = rememberVectorPainter(Icons.Filled.Person),
        )

    @Composable
    override fun Content() {
        Scaffold { paddingValues ->
            EmptyScreen(
                message = "动作库、体重记录与设置",
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}
