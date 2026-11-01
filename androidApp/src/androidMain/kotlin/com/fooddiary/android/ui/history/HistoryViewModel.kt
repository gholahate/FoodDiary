package com.fooddiary.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.fooddiary.android.AppContainer
import com.fooddiary.domain.DailyTotals
import com.fooddiary.domain.Macros
import com.fooddiary.domain.UserProfile
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class HistoryCalendarState(
    val year: Int,
    val month: Int, // 1-12
    /** Day whose summary is shown under the calendar (first tap); null = none yet. */
    val selectedDate: String? = null, // YYYY-MM-DD
)

/** Summary of the selected day: totals (null = no entries that day) and the goals to compare with. */
data class DayPreview(
    val date: String,
    val totals: Macros?,
    val profile: UserProfile?,
)

/**
 * Holds calendar navigation state (which month is displayed, which date is
 * selected). The actual diary content for the selected date is driven by a
 * separate DiaryDayViewModel that the screen creates alongside this one and
 * re-points via DiaryDayViewModel.changeDate() whenever the selection changes.
 */
/** Colour of a calendar day's dot: calories within the daily goal, or over it. */
enum class DayGoalStatus { WITHIN_GOAL, OVER_GOAL }

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private val _state = MutableStateFlow(
        HistoryCalendarState(year = today.year, month = today.monthNumber),
    )
    val state: StateFlow<HistoryCalendarState> = _state

    /**
     * Dot per day of the displayed month, by calories only: green if the day's
     * real total (no weekly carry-over) is within the goal, red if over.
     * No dot for days without entries, for future days, or when no calorie
     * goal is set. Live: updates as soon as entries or the goal change.
     */
    val dayStatuses: StateFlow<Map<String, DayGoalStatus>> =
        combine(
            _state.map { it.year to it.month }.distinctUntilChanged().flatMapLatest { (year, month) ->
                val first = LocalDate(year, month, 1)
                val last = first.plus(DatePeriod(months = 1)).plus(DatePeriod(days = -1))
                container.diaryRepository.observeDailyTotalsInRange(first.toString(), last.toString())
            },
            container.profileRepository.observeProfile(),
        ) { totals, profile ->
            val target = profile?.targetKcal
            if (target == null || target <= 0.0) return@combine emptyMap<String, DayGoalStatus>()
            val todayIso = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
            totals
                .filter { it.date <= todayIso } // ISO dates compare correctly as text
                .associate { day ->
                    day.date to if (day.macros.kcal > target) DayGoalStatus.OVER_GOAL else DayGoalStatus.WITHIN_GOAL
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Live summary of the selected day; updates if entries or goals change. */
    val preview: StateFlow<DayPreview?> =
        combine(
            _state.map { it.selectedDate }.distinctUntilChanged().flatMapLatest { date ->
                if (date == null) {
                    flowOf<Pair<String, Macros?>?>(null)
                } else {
                    container.diaryRepository.observeDailyTotalsInRange(date, date)
                        .map<List<DailyTotals>, Pair<String, Macros?>?> { rows -> date to rows.firstOrNull()?.macros }
                }
            },
            container.profileRepository.observeProfile(),
        ) { selected, profile ->
            selected?.let { (date, totals) -> DayPreview(date, totals, profile) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** «Сегодня»: jump back to the current month and select today. */
    fun goToToday() {
        val now = Clock.System.todayIn(TimeZone.currentSystemDefault())
        _state.value = HistoryCalendarState(year = now.year, month = now.monthNumber, selectedDate = now.toString())
    }

    fun selectDate(date: LocalDate) {
        _state.value = _state.value.copy(selectedDate = date.toString())
    }

    fun goToPreviousMonth() {
        val s = _state.value
        val newMonth = if (s.month == 1) 12 else s.month - 1
        val newYear = if (s.month == 1) s.year - 1 else s.year
        _state.value = s.copy(year = newYear, month = newMonth)
    }

    fun goToNextMonth() {
        val s = _state.value
        val newMonth = if (s.month == 12) 1 else s.month + 1
        val newYear = if (s.month == 12) s.year + 1 else s.year
        _state.value = s.copy(year = newYear, month = newMonth)
    }
}

class HistoryViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == HistoryViewModel::class.java)
        return HistoryViewModel(container) as T
    }
}
