package com.fitplan.ui.stats

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Tab

object StatsTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 2u,
            title = "统计",
            icon = rememberVectorPainter(Icons.Filled.BarChart),
        )

    @Composable
    override fun Content() {
        Scaffold { paddingValues ->
            EmptyScreen(
                message = "训练统计",
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}
