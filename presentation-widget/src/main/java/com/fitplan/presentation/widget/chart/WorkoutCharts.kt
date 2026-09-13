package com.fitplan.presentation.widget.chart

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

/** 图表上的一个数据点：[label] 画在横轴上，[value] 决定柱高或折线位置。 */
data class ChartEntry(
    val label: String,
    val value: Double,
)

/** 横轴最多摆这么多标签，再多的日子就隔几个标一个，免得糊成一团。 */
private const val MAX_AXIS_LABELS = 6

private fun labelSpacing(count: Int): Int =
    if (count <= MAX_AXIS_LABELS) 1 else (count + MAX_AXIS_LABELS - 1) / MAX_AXIS_LABELS

/**
 * 横轴点一多（比如近 30 天），Vico 默认只会保住 1 倍缩放，
 * 结果大部分点被挤出可视区；这里改成一律按内容缩放，整段数据才画得下。
 */
@Composable
private fun contentZoomState(): VicoZoomState =
    rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content)

/**
 * 柱状图，统计页的容量趋势 / 训练频率 / 肌群分布共用。
 * 只认「标签 + 数值」，文案与配色由调用方决定。
 */
@Composable
fun ColumnChart(
    entries: List<ChartEntry>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val labelsKey = remember { ExtraStore.Key<List<String>>() }
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(entries) {
        modelProducer.runTransaction {
            columnModel { series(entries.map { it.value }) }
            extras { it[labelsKey] = entries.map { it.label } }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(
                        fill = Fill(color),
                        thickness = 10.dp,
                        shape = RoundedCornerShape(3.dp),
                    ),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing(entries.size) }),
                valueFormatter = labelFormatter(labelsKey),
            ),
        ),
        modelProducer = modelProducer,
        zoomState = contentZoomState(),
        modifier = modifier.fillMaxWidth().height(180.dp),
    )
}

/** 折线图，统计页的动作重量进步用它。 */
@Composable
fun LineChart(
    entries: List<ChartEntry>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.tertiary
    val labelsKey = remember { ExtraStore.Key<List<String>>() }
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(entries) {
        modelProducer.runTransaction {
            lineModel { series(entries.map { it.value }) }
            extras { it[labelsKey] = entries.map { it.label } }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(Fill(color))),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing(entries.size) }),
                valueFormatter = labelFormatter(labelsKey),
            ),
        ),
        modelProducer = modelProducer,
        zoomState = contentZoomState(),
        modifier = modifier.fillMaxWidth().height(180.dp),
    )
}

/**
 * 横轴把 x 值当作索引，去 [labelsKey] 里取调用方塞进来的标签。
 * 取不到时退回索引本身：Vico 不允许这里返回空串。
 */
private fun labelFormatter(labelsKey: ExtraStore.Key<List<String>>): CartesianValueFormatter =
    CartesianValueFormatter { context, value, _ ->
        val index = value.toInt()
        context.extraStore.getOrNull(labelsKey)?.getOrNull(index) ?: index.toString()
    }
