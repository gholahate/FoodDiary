package com.fooddiary.android.ui.history

import androidx.compose.foundation.background
import kotlin.math.roundToInt
import com.fooddiary.android.ui.common.horizontalSwipe
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fooddiary.android.AppContainer
import com.fooddiary.android.ui.common.rememberCurrentDate
import com.fooddiary.android.ui.theme.StatusColors
import kotlinx.datetime.LocalDate

/**
 * The History tab shows a month calendar. Tapping a day opens a dedicated
 * full-screen DayDetailScreen for that date (see onOpenDay) — the calendar
 * and the diary/catalog editing panels each need their own screen's worth of
 * room, so they no longer share one cramped layout.
 */
@Composable
fun HistoryScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    onOpenDay: (String) -> Unit = {},
) {
    val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModelFactory(container))
    val calendarState by historyViewModel.state.collectAsState()
    val dayStatuses by historyViewModel.dayStatuses.collectAsState()
    val preview by historyViewModel.preview.collectAsState()
    val todayIso by rememberCurrentDate()
    val today = LocalDate.parse(todayIso)

    // «Сегодня» appears once the user has moved away from today's month or day.
    val awayFromToday = calendarState.year != today.year ||
        calendarState.month != today.monthNumber ||
        (calendarState.selectedDate != null && calendarState.selectedDate != todayIso)

    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        CalendarHeader(
            year = calendarState.year,
            month = calendarState.month,
            onPreviousMonth = historyViewModel::goToPreviousMonth,
            onNextMonth = historyViewModel::goToNextMonth,
            showToday = awayFromToday,
            onToday = historyViewModel::goToToday,
        )
        // Swipe left/right over the calendar flips months.
        Box(
            modifier = Modifier.horizontalSwipe(
                onSwipeLeft = historyViewModel::goToNextMonth,
                onSwipeRight = historyViewModel::goToPreviousMonth,
            ),
        ) {
            CalendarGridView(
                year = calendarState.year,
                month = calendarState.month,
                today = today,
                selectedDate = calendarState.selectedDate,
                dayStatuses = dayStatuses,
                onDateSelected = { date ->
                    // 1st tap: select + summary below; 2nd tap on the same day: open it.
                    if (calendarState.selectedDate == date.toString()) {
                        onOpenDay(date.toString())
                    } else {
                        historyViewModel.selectDate(date)
                    }
                },
            )
        }

        val current = preview
        if (current == null || calendarState.selectedDate == null) {
            Text(
                "Нажмите на день — появится сводка. Нажмите ещё раз — откроется день.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else {
            DayPreviewCard(
                preview = current,
                isToday = current.date == todayIso,
                onOpen = { onOpenDay(current.date) },
            )
        }
    }
}

/** Summary card under the calendar: КБЖУ of the selected day against the daily goals. */
@Composable
private fun DayPreviewCard(preview: DayPreview, isToday: Boolean, onOpen: () -> Unit) {
    val date = LocalDate.parse(preview.date)
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${date.dayOfMonth} ${MONTH_NAMES_RU[date.monthNumber - 1].lowercase()} ${date.year}" +
                    if (isToday) " (сегодня)" else "",
                fontWeight = FontWeight.Bold,
            )
            val totals = preview.totals
            if (totals == null) {
                Text("Записей нет", style = MaterialTheme.typography.bodyMedium)
            } else {
                val p = preview.profile
                PreviewLine("Калории", totals.kcal, p?.targetKcal, "ккал")
                PreviewLine("Белки", totals.protein, p?.targetProtein, "г")
                PreviewLine("Жиры", totals.fat, p?.targetFat, "г")
                PreviewLine("Углеводы", totals.carb, p?.targetCarb, "г")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Нажмите на день ещё раз, чтобы открыть",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpen) { Text("Открыть") }
            }
        }
    }
}

@Composable
private fun PreviewLine(label: String, value: Double, target: Double?, unit: String) {
    val over = target != null && value > target
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            if (target != null) "${value.roundToInt()} из ${target.roundToInt()} $unit" else "${value.roundToInt()} $unit",
            color = if (over) StatusColors.over else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (over) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CalendarHeader(
    year: Int,
    month: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    showToday: Boolean,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPreviousMonth) { Text("◀") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${MONTH_NAMES_RU[month - 1]} $year",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (showToday) {
                IconButton(onClick = onToday) {
                    Icon(Icons.Filled.Today, contentDescription = "Вернуться к сегодняшнему дню")
                }
            }
        }
        TextButton(onClick = onNextMonth) { Text("▶") }
    }
}

@Composable
private fun CalendarGridView(
    year: Int,
    month: Int,
    today: LocalDate,
    selectedDate: String?,
    dayStatuses: Map<String, DayGoalStatus>,
    onDateSelected: (LocalDate) -> Unit,
) {
    val cells = buildCalendarGrid(year, month)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_SHORT_RU.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        // Chunk the flat cell list into weeks of 7 for row-by-row rendering.
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    CalendarDayCell(
                        cell = cell,
                        isSelected = cell.date?.toString() == selectedDate,
                        isToday = cell.date == today,
                        status = cell.date?.let { dayStatuses[it.toString()] },
                        onClick = { cell.date?.let(onDateSelected) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad the last week row with blank cells so every row has 7 columns.
                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    cell: CalendarCell,
    isSelected: Boolean,
    isToday: Boolean,
    status: DayGoalStatus?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = cell.date
    val backgroundColor = when {
        date == null -> Color.Transparent
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .let { m -> if (date != null) m.clickable(onClick = onClick) else m },
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Text(
                date.dayOfMonth.toString(),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
            // Calorie-goal dot under the number: green = within goal, red = over.
            if (status != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (status == DayGoalStatus.OVER_GOAL) StatusColors.over else StatusColors.ok),
                )
            }
        }
    }
}

