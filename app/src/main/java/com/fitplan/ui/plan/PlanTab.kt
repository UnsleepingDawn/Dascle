package com.fitplan.ui.plan

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.core.screens.EmptyScreen
import com.fitplan.presentation.util.Tab

object PlanTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 1u,
            title = "计划",
            icon = rememberVectorPainter(Icons.Filled.FitnessCenter),
        )

    @Composable
    override fun Content() {
        Scaffold { paddingValues ->
            EmptyScreen(
                message = "训练计划与日程",
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}
