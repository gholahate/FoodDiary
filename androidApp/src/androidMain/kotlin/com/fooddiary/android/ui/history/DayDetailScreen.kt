package com.fooddiary.android.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fooddiary.android.AppContainer
import com.fooddiary.android.ui.diary.DiaryDayContent
import com.fooddiary.android.ui.diary.DiaryDayViewModel
import com.fooddiary.android.ui.diary.DiaryDayViewModelFactory
import com.fooddiary.android.ui.common.horizontalSwipe
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Full-screen day editor opened from the History calendar. Reuses the same
 * DiaryDayContent as the Today screen, but without the trailing weekly mini
 * chart, since on a past-day screen the diary/catalog panels need the room
 * more than a chart does — the full weekly/monthly picture lives on the
 * Charts tab instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    container: AppContainer,
    date: String,
    onBack: () -> Unit,
    onEditProduct: (String) -> Unit,
    /** Go to another day (swipe or the ◀ ▶ arrows); the screen reopens on that date. */
    onChangeDate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = LocalDate.parse(date)
    val goPrevious = { onChangeDate(current.minus(DatePeriod(days = 1)).toString()) }
    val goNext = { onChangeDate(current.plus(DatePeriod(days = 1)).toString()) }

    val viewModel: DiaryDayViewModel = viewModel(
        key = "day-detail-$date",
        factory = DiaryDayViewModelFactory(container, date),
    )
    val state by viewModel.state.collectAsState()

    // Swipe right = previous day, swipe left = next day (like turning pages).
    Column(modifier = modifier.fillMaxWidth().horizontalSwipe(onSwipeLeft = goNext, onSwipeRight = goPrevious)) {
        TopAppBar(
            title = { Text(formatDayDetailTitle(date)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Назад к календарю")
                }
            },
            actions = {
                IconButton(onClick = goPrevious) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Предыдущий день")
                }
                IconButton(onClick = goNext) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Следующий день")
                }
            },
        )

        DiaryDayContent(
            state = state,
            diaryPanelTitle = "Учтено в этот день",
            searchQuery = state.searchQuery,
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            onAddToDiary = viewModel::addToDiary,
            onRemoveFromDiary = viewModel::removeFromDiary,
            onEditProduct = onEditProduct,
            onDeleteProduct = viewModel::deleteProduct,
            onDismissNotice = viewModel::dismissNotice,
            showWeeklyChart = false,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
        )
    }
}

private fun formatDayDetailTitle(isoDate: String): String {
    val date = LocalDate.parse(isoDate)
    return "${date.dayOfMonth} ${MONTH_NAMES_RU[date.monthNumber - 1].lowercase()} ${date.year}"
}
