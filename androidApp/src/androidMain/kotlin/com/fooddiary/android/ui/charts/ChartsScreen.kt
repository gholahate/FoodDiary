package com.fooddiary.android.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.fooddiary.android.data.ChartStyle
import com.fooddiary.android.ui.theme.StatusColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fooddiary.android.AppContainer
import com.fooddiary.domain.Macros
import com.fooddiary.usecase.StatsPoint
import com.fooddiary.usecase.StatsStep
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: ChartsViewModel = viewModel(factory = ChartsViewModelFactory(container))
    val state by viewModel.state.collectAsState()
    val chartStyle by container.settings.chartStyle.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Графики", style = MaterialTheme.typography.headlineSmall)

        Text(
            "Параметр",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartMacro.entries.forEach { macro ->
                FilterChip(
                    selected = state.macro == macro,
                    onClick = { viewModel.onMacroSelected(macro) },
                    label = { Text(macro.label) },
                )
            }
        }

        Text(
            "Шаг",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.step == StatsStep.DAY,
                onClick = { viewModel.onStepSelected(StatsStep.DAY) },
                label = { Text("День") },
            )
            FilterChip(
                selected = state.step == StatsStep.WEEK,
                onClick = { viewModel.onStepSelected(StatsStep.WEEK) },
                label = { Text("Неделя (сумма)") },
            )
        }

        Text(
            "Период",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = false, onClick = { viewModel.selectPreset(7) }, label = { Text("7 дней") })
            FilterChip(selected = false, onClick = { viewModel.selectPreset(30) }, label = { Text("30 дней") })
            FilterChip(selected = false, onClick = { viewModel.selectPreset(90) }, label = { Text("90 дней") })
        }
        Text(
            "${state.startDate} — ${state.endDate}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.points.isEmpty()) {
            Text(
                "Нет данных за выбранный период",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 32.dp),
            )
        } else {
            when (chartStyle) {
                ChartStyle.BARS -> BarChart(
                    points = state.points,
                    macro = state.macro,
                    modifier = Modifier.padding(top = 24.dp),
                )
                ChartStyle.LINE -> LineChart(
                    points = state.points,
                    macro = state.macro,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun BarChart(points: List<StatsPoint>, macro: ChartMacro, modifier: Modifier = Modifier) {
    val values = points.map { it.macros.valueFor(macro) }
    val max = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val barWidth = 40.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            points.forEachIndexed { index, point ->
                val value = values[index]
                val heightFraction = (value / max).toFloat().coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.width(barWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        value.roundToInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((120 * heightFraction).dp)
                                .background(StatusColors.ok),
                        )
                    }
                    Text(
                        shortenAxisLabel(point.label),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            "${macro.label}, ${macro.unit}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Same columns as [BarChart] (40dp wide, 8dp apart, so labels line up the same
 * way), but the values are dots joined by a line drawn across the whole row.
 */
@Composable
private fun LineChart(points: List<StatsPoint>, macro: ChartMacro, modifier: Modifier = Modifier) {
    val values = points.map { it.macros.valueFor(macro) }
    val max = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val column = 40.dp
    val gap = 8.dp
    val totalWidth = column * points.size + gap * (points.size - 1).coerceAtLeast(0)
    val lineColor = StatusColors.ok
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                values.forEach { value ->
                    Text(
                        value.roundToInt().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(column),
                    )
                }
            }
            Canvas(modifier = Modifier.width(totalWidth).height(120.dp).padding(vertical = 6.dp)) {
                val step = (column + gap).toPx()
                val half = column.toPx() / 2
                val h = size.height
                // Baseline (zero) for orientation.
                drawLine(gridColor, Offset(0f, h), Offset(size.width, h), strokeWidth = 1.dp.toPx())
                val offsets = values.mapIndexed { i, v ->
                    Offset(i * step + half, h - (v / max).toFloat().coerceIn(0f, 1f) * h)
                }
                if (offsets.size > 1) {
                    val path = Path().apply {
                        moveTo(offsets.first().x, offsets.first().y)
                        offsets.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                offsets.forEach { drawCircle(lineColor, radius = 4.dp.toPx(), center = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                points.forEach { point ->
                    Text(
                        shortenAxisLabel(point.label),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(column),
                    )
                }
            }
        }
        Text(
            "${macro.label}, ${macro.unit}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * DAY-step labels look like "2026-09-22" -> shorten to "09-22".
 * WEEK-step labels look like "2026-W38" -> already short, keep as-is.
 */
private fun shortenAxisLabel(label: String): String =
    if (label.contains("-W")) label else label.takeLast(5)

private fun Macros.valueFor(macro: ChartMacro): Double = when (macro) {
    ChartMacro.KCAL -> kcal
    ChartMacro.PROTEIN -> protein
    ChartMacro.FAT -> fat
    ChartMacro.CARB -> carb
}
