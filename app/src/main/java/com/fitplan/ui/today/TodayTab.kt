package com.fitplan.ui.today

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Tab

object TodayTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 0u,
            title = "今日",
            icon = rememberVectorPainter(Icons.Filled.Today),
        )

    @Composable
    override fun Content() {
        Scaffold { paddingValues ->
            EmptyScreen(
                message = "今日训练安排",
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}
