package com.fooddiary.android.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fooddiary.android.AppContainer
import com.fooddiary.usecase.StatsPoint
import com.fooddiary.usecase.StatsStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

enum class ChartMacro(val label: String, val unit: String) {
    KCAL("Калории", "ккал"),
    PROTEIN("Белки", "г"),
    FAT("Жиры", "г"),
    CARB("Углеводы", "г"),
}

data class ChartsUiState(
    val macro: ChartMacro = ChartMacro.KCAL,
    val step: StatsStep = StatsStep.DAY,
    val startDate: String = "",
    val endDate: String = "",
    val points: List<StatsPoint> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Selection inputs bundled together so a single flatMapLatest drives fetching
 * — any new selection automatically cancels an in-flight fetch for the
 * previous one, so a slow response for a stale selection can never land after
 * a newer selection's result.
 */
private data class ChartsSelection(
    val macro: ChartMacro,
    val step: StatsStep,
    val startDate: String,
    val endDate: String,
)

class ChartsViewModel(private val container: AppContainer) : ViewModel() {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val defaultStart = today.minus(DatePeriod(days = 29)).toString()
    private val defaultEnd = today.toString()

    private val _macro = MutableStateFlow(ChartMacro.KCAL)
    private val _step = MutableStateFlow(StatsStep.DAY)
    private val _startDate = MutableStateFlow(defaultStart)
    private val _endDate = MutableStateFlow(defaultEnd)

    private val selection: Flow<ChartsSelection> =
        combine(_macro, _step, _startDate, _endDate) { macro, step, start, end ->
            ChartsSelection(macro, step, start, end)
        }

    private val _state = MutableStateFlow(
        ChartsUiState(startDate = defaultStart, endDate = defaultEnd),
    )
    val state: StateFlow<ChartsUiState> = _state

    init {
        selection.onEach { sel ->
            _state.value = _state.value.copy(
                macro = sel.macro,
                step = sel.step,
                startDate = sel.startDate,
                endDate = sel.endDate,
                isLoading = true,
            )
        }.flatMapLatest { sel ->
            // Live subscription (not a one-shot fetch): re-emits whenever a
            // diary entry in this range is added or deleted, even from another
            // screen, so this long-lived tab never shows stale data.
            container.getStatsUseCase.observe(sel.startDate, sel.endDate, sel.step)
                .map { points -> sel to points }
        }.onEach { (sel, points) ->
            // flatMapLatest already guarantees this is the latest selection's result.
            _state.value = _state.value.copy(
                macro = sel.macro,
                step = sel.step,
                startDate = sel.startDate,
                endDate = sel.endDate,
                points = points,
                isLoading = false,
            )
        }.launchIn(viewModelScope)
    }

    fun onMacroSelected(macro: ChartMacro) {
        _macro.value = macro
    }

    fun onStepSelected(step: StatsStep) {
        _step.value = step
    }

    /** Convenience presets so the user doesn't have to type dates by hand. */
    fun selectPreset(days: Int) {
        val end = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val start = end.minus(DatePeriod(days = days - 1))
        _startDate.value = start.toString()
        _endDate.value = end.toString()
    }
}

class ChartsViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == ChartsViewModel::class.java)
        return ChartsViewModel(container) as T
    }
}
