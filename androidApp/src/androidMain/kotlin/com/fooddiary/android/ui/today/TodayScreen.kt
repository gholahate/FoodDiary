package com.fooddiary.android.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fooddiary.android.AppContainer
import com.fooddiary.android.ui.common.rememberCurrentDate
import com.fooddiary.android.ui.diary.DiaryDayContent
import com.fooddiary.android.ui.diary.DiaryDayViewModel
import com.fooddiary.android.ui.diary.DiaryDayViewModelFactory

/**
 * "Today" is simply the day-diary view pinned to the current date — all the
 * actual UI and state logic lives in DiaryDayContent / DiaryDayViewModel,
 * which the History screen also uses for arbitrary past/future dates.
 */
@Composable
fun TodayScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    onEditProduct: (String) -> Unit = {},
    onAddProduct: () -> Unit = {},
    onScanProduct: () -> Unit = {},
    /** When non-null, put this text into the search box once (e.g. a just-scanned product's name). */
    searchRequest: String? = null,
    onSearchRequestConsumed: () -> Unit = {},
) {
    val viewModel: DiaryDayViewModel = viewModel(
        factory = DiaryDayViewModelFactory(container),
        key = "today",
    )
    val state by viewModel.state.collectAsState()

    // Follow the calendar: when midnight passes while the app is open, this
    // screen switches to the new day by itself, so new entries go to the right
    // date (previously it stayed on yesterday until the app was restarted).
    val today by rememberCurrentDate()
    LaunchedEffect(today) {
        viewModel.changeDate(today)
    }

    LaunchedEffect(searchRequest) {
        if (searchRequest != null) {
            viewModel.onSearchQueryChanged(searchRequest)
            onSearchRequestConsumed()
        }
    }

    DiaryDayContent(
        state = state,
        diaryPanelTitle = "Учтено сегодня",
        searchQuery = state.searchQuery,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onAddToDiary = viewModel::addToDiary,
        onRemoveFromDiary = viewModel::removeFromDiary,
        onEditProduct = onEditProduct,
        onDeleteProduct = viewModel::deleteProduct,
        onDismissNotice = viewModel::dismissNotice,
        onAddProduct = onAddProduct,
        onScanProduct = onScanProduct,
        modifier = modifier,
    )
}
