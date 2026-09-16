package com.fitplan.presentation.widget.chart

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent

/** 柱状图上的一个数据点：[label] 画在横轴上，[value] 决定柱高。 */
data class ChartEntry(
    val label: String,
    val value: Double,
)

/**
 * 折线图上的一个数据点。
 *
 * [x] 是横轴坐标，不再固定是序号：等间距模式下就是数据点的序号，
 * 按日期模式下是「区间第几天」，于是没训练的日子会自然留出空档。
 */
data class LinePoint(
    val x: Int,
    val value: Double,
)

/** 横轴最多摆这么多标签，再多的日子就隔几个标一个，免得糊成一团。 */
private const val MAX_AXIS_LABELS = 6

/** 图表高度，两张图保持一致。 */
private val CHART_HEIGHT = 180.dp

/** 折线的粗细与空心圆点的大小。 */
private val LINE_THICKNESS = 2.dp
private val POINT_SIZE = 8.dp
private val POINT_STROKE_THICKNESS = 1.5.dp

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
 * 横轴把 x 值当作索引去 [labelAt] 取文案。
 * 取不到（返回空串）时退回索引本身：Vico 不允许这里返回空串。
 */
private fun xLabelFormatter(labelAt: (Int) -> String): CartesianValueFormatter =
    CartesianValueFormatter { _, value, _ ->
        val index = value.toInt()
        labelAt(index).ifBlank { index.toString() }
    }

/**
 * 柱状图，统计页的肌群组数分布用它。
 *
 * 横轴是分类轴：每个柱位一个标签，不跳号，所以肌群再多也每个都带自己的名字。
 */
@Composable
fun ColumnChart(
    entries: List<ChartEntry>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val modelProducer = remember { CartesianChartModelProducer() }
    val labels = remember(entries) { entries.map { it.label } }
    val formatter = remember(labels) { xLabelFormatter { labels.getOrElse(it) { "" } } }
    val itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned() }

    LaunchedEffect(entries) {
        modelProducer.runTransaction {
            columnModel { series(entries.map { it.value }) }
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
                itemPlacer = itemPlacer,
                valueFormatter = formatter,
            ),
        ),
        modelProducer = modelProducer,
        zoomState = contentZoomState(),
        modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
    )
}

/**
 * 折线图，统计页的动作重量进步用它。
 *
 * [labelAt] 把横轴坐标换算成标签文案，缺数据的日子也会被问到，
 * 所以按日期显示时横向的空档位置同样有日期，不会露出「0、1」这种序号。
 */
@Composable
fun LineChart(
    points: List<LinePoint>,
    labelAt: (Int) -> String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val span = remember(points) {
        if (points.isEmpty()) 1 else points.maxOf { it.x } - points.minOf { it.x } + 1
    }
    val spacing = remember(span) { labelSpacing(span) }
    val formatter = remember(labelAt) { xLabelFormatter(labelAt) }
    val itemPlacer = remember(spacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { spacing }) }

    // 空心圆点：透明填充打底，只用主色描一圈。
    val marker = rememberShapeComponent(
        fill = Fill(Color.Transparent),
        shape = CircleShape,
        strokeFill = Fill(color),
        strokeThickness = POINT_STROKE_THICKNESS,
    )

    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineModel {
                series(points.map { it.x.toDouble() }, points.map { it.value })
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(color)),
                        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = LINE_THICKNESS),
                        pointProvider = LineCartesianLayer.PointProvider.single(
                            LineCartesianLayer.Point(component = marker, size = POINT_SIZE),
                        ),
                    ),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                itemPlacer = itemPlacer,
                valueFormatter = formatter,
            ),
        ),
        modelProducer = modelProducer,
        zoomState = contentZoomState(),
        modifier = modifier.fillMaxWidth().height(CHART_HEIGHT),
    )
}
