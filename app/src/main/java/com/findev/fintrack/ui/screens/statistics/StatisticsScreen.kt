package com.findev.fintrack.ui.screens.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.data.MonthlyBar
import com.findev.fintrack.ui.PeriodFilterBar
import com.findev.fintrack.ui.ChipRow
import com.findev.fintrack.ui.formatMinor
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory = state.selectedCategory
    val monthsHaveData = state.months.any { it.incomeMinor > 0 || it.expenseMinor > 0 }

    Scaffold(
        // The app-level Scaffold already applies the status bar inset.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.quick_entry_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // A single account is the same as "all", so the filter only earns its space
            // once there is a real choice to make.
            if (state.accounts.size >= 2) {
                item {
                    val allLabel = stringResource(R.string.stats_all_accounts)
                    ChipRow(
                        items = listOf("" to allLabel) + state.accounts.map { it.id to it.name },
                        selectedId = state.selectedAccountId ?: "",
                        onSelected = { id -> viewModel.onAccountChange(id.ifEmpty { null }) },
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.stats_categories_title)) }
            item {
                PeriodFilterBar(
                    selection = state.selection,
                    onPeriodChange = viewModel::onPeriodChange,
                    onCustomRangeChange = viewModel::onCustomRangeChange,
                )
            }

            if (state.isEmpty) {
                item {
                    Text(
                        text = stringResource(R.string.stats_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                item { DonutChart(slices = state.slices, totalMinor = state.totalMinor) }
                items(state.slices, key = { it.total.categoryId }) { slice ->
                    LegendRow(
                        slice = slice,
                        selected = slice.total.categoryId == selectedCategory?.id,
                        onClick = { viewModel.onCategoryToggle(slice.total) },
                    )
                }
            }

            // Monthly section. In category mode it always shows (with its own empty note) so a
            // tap never looks like it did nothing; in overview mode it hides when there is no data.
            if (selectedCategory != null || monthsHaveData) {
                item {
                    MonthlySectionHeader(
                        selectedCategory = selectedCategory,
                        onClear = viewModel::onClearCategory,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (selectedCategory != null && !monthsHaveData) {
                    item {
                        Text(
                            text = stringResource(R.string.stats_category_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        MonthlyChart(
                            months = state.months,
                            categoryColor = selectedCategory?.color,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun MonthlySectionHeader(
    selectedCategory: CategoryRef?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val title = if (selectedCategory == null) {
            stringResource(R.string.stats_monthly_title)
        } else {
            stringResource(R.string.stats_category_trend, "${selectedCategory.icon} ${selectedCategory.name}")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (selectedCategory != null) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.stats_show_all_categories))
            }
        }
    }
}

@Composable
private fun DonutChart(slices: List<CategorySlice>, totalMinor: Long) {
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val stroke = Stroke(width = 36.dp.toPx())
            val inset = stroke.width / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = slice.fraction * 360f
                drawArc(
                    color = Color(slice.total.categoryColor),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                startAngle += sweep
            }
        }
        Text(
            text = stringResource(R.string.money_with_currency, formatMinor(totalMinor)),
            style = MaterialTheme.typography.titleLarge,
            color = onSurface,
        )
    }
}

@Composable
private fun LegendRow(slice: CategorySlice, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(14.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(Color(slice.total.categoryColor))
            }
        }
        Text(
            text = "${slice.total.categoryIcon} ${slice.total.categoryName}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.stats_percent, (slice.fraction * 100).roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.money_with_currency, formatMinor(slice.total.totalMinor)),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MonthlyChart(months: List<MonthlyBar>, categoryColor: Long?) {
    val incomeColor = MaterialTheme.colorScheme.primary
    val expenseColor = MaterialTheme.colorScheme.error
    val singleColor = categoryColor?.let { Color(it) }
    val modelProducer = remember { CartesianChartModelProducer() }
    val monthFormat = remember { DateTimeFormatter.ofPattern("LLL", Locale("ru")) }

    LaunchedEffect(months, singleColor) {
        modelProducer.runTransaction {
            // Rubles, not kopecks: a chart is a picture and 5000 reads where 500000 does not.
            columnSeries {
                if (singleColor == null) {
                    // Overview: two grouped columns per month, income then expense.
                    series(months.map { it.incomeMinor / 100.0 })
                    series(months.map { it.expenseMinor / 100.0 })
                } else {
                    // Category mode: one expense column per month.
                    series(months.map { it.expenseMinor / 100.0 })
                }
            }
        }
    }

    // Vico plots x as 0..n, so month names are looked up by column position.
    val bottomFormatter = CartesianValueFormatter { _, x, _ ->
        months.getOrNull(x.toInt())?.month?.format(monthFormat).orEmpty()
    }

    val incomeColumn = rememberLineComponent(fill = Fill(incomeColor), thickness = 10.dp)
    val expenseColumn = rememberLineComponent(fill = Fill(expenseColor), thickness = 10.dp)
    val categoryColumn = rememberLineComponent(fill = Fill(singleColor ?: expenseColor), thickness = 14.dp)

    val columnProvider = if (singleColor == null) {
        ColumnCartesianLayer.ColumnProvider.series(incomeColumn, expenseColumn)
    } else {
        ColumnCartesianLayer.ColumnProvider.series(categoryColumn)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(columnProvider = columnProvider),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )
        if (singleColor == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SeriesKey(color = incomeColor, label = stringResource(R.string.stats_income))
                SeriesKey(color = expenseColor, label = stringResource(R.string.stats_expense))
            }
        }
    }
}

@Composable
private fun SeriesKey(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(12.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawCircle(color) }
        }
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
