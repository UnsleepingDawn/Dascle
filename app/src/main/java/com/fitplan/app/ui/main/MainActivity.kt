package com.fitplan.app.ui.main

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitplan.app.di.AppGraph
import com.fitplan.core.metro.metroGraph
import com.fitplan.domain.ui.model.AppTheme
import com.fitplan.presentation.core.components.CheckboxItem
import com.fitplan.presentation.core.components.CollapsibleBox
import com.fitplan.presentation.core.components.HeadingItem
import com.fitplan.presentation.core.components.Pill
import com.fitplan.presentation.core.components.SliderItem
import com.fitplan.presentation.core.components.material.Scaffold
import com.fitplan.presentation.theme.FitPlanTheme

class MainActivity : ComponentActivity() {

    private val graph: AppGraph by lazy { metroGraph() }

    override fun onCreate(savedInstanceState: Bundle?) {
        graph.inject(this)
        super.onCreate(savedInstanceState)

        Log.d("FitPlan", "MainActivity injected with $graph")

        setContent {
            var appTheme by remember { mutableStateOf(AppTheme.DEFAULT) }
            FitPlanTheme(appTheme = appTheme) {
                Scaffold { paddingValues ->
                    ThemeDemoContent(
                        appTheme = appTheme,
                        onThemeChange = { appTheme = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    )
                }
            }
        }
    }
}

/**
 * M3 验收用的临时演示页，M5 接入导航骨架后会被真正的首页替换。
 */
@Composable
private fun ThemeDemoContent(
    appTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    var restSeconds by remember { mutableIntStateOf(90) }
    var autoRest by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeadingItem(text = "主题配色")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppTheme.entries.forEach { theme ->
                FilterChip(
                    selected = theme == appTheme,
                    onClick = { onThemeChange(theme) },
                    label = { Text(text = theme.name) },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        HeadingItem(text = "组件预览")
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Pill(text = "胸", style = MaterialTheme.typography.bodyMedium)
            Pill(text = "3 组", style = MaterialTheme.typography.bodyMedium)
            Pill(text = "60 kg", style = MaterialTheme.typography.bodyMedium)
            Pill(text = "12 次", style = MaterialTheme.typography.bodyMedium)
        }

        CollapsibleBox(heading = "今日安排") {
            Text(
                text = "平板杠铃卧推 · 4 组 × 8 次",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Text(
                text = "坐姿推胸 · 3 组 × 12 次",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        CheckboxItem(
            label = "自动开始组间休息",
            checked = autoRest,
            onClick = { autoRest = !autoRest },
        )

        SliderItem(
            value = restSeconds,
            valueRange = 15..180,
            label = "默认组间休息",
            valueString = "${restSeconds}s",
            onChange = { restSeconds = it },
        )
    }
}
