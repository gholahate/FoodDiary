package com.fooddiary.android.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fooddiary.android.AppContainer
import com.fooddiary.domain.DiaryEntry
import com.fooddiary.domain.Macros
import com.fooddiary.domain.Product
import com.fooddiary.domain.ProductUnit
import com.fooddiary.domain.UserProfile
import com.fooddiary.usecase.DeleteProductResult
import com.fooddiary.usecase.StatsPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

data class DiaryDayUiState(
    val date: String = "",
    val entries: List<DiaryEntry> = emptyList(),
    val catalog: List<Product> = emptyList(),
    val searchQuery: String = "",
    val dailyTotals: Macros = Macros.ZERO,
    val profile: UserProfile? = null,
    val weeklyPoints: List<StatsPoint> = emptyList(),
    val productNamesById: Map<String, String> = emptyMap(),
    /** Household measures per product id, for the catalog cards' "Вес / мера" choice. */
    val unitsByProduct: Map<String, List<ProductUnit>> = emptyMap(),
    /** The whole product base is empty (not just the search result) → show the "how to start" hint. */
    val catalogIsEmpty: Boolean = false,
    /** A message to show in a dialog (e.g. why a product can't be deleted); null = none. */
    val notice: String? = null,
)

/**
 * Drives a single day's diary view: entries for that day, the full product
 * catalog (optionally filtered by search), daily totals, the user's profile
 * (for goal progress bars), and a trailing 7-day mini chart ending on the
 * selected date.
 *
 * Used by both the Today screen (pinned to today's date) and the History
 * screen (date changes as the user browses the calendar, via [changeDate]).
 */
class DiaryDayViewModel(
    private val container: AppContainer,
    initialDate: String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString(),
) : ViewModel() {

    private val _date = MutableStateFlow(initialDate)
    private val _searchQuery = MutableStateFlow("")
    private val _state = MutableStateFlow(DiaryDayUiState(date = initialDate))
    private var catalogIsEmptyNow = false
    val state: StateFlow<DiaryDayUiState> = _state

    init {
        combine(
            _date.flatMapLatest { container.diaryRepository.observeEntriesForDate(it) },
            container.productRepository.observeAll(),
            container.profileRepository.observeProfile(),
            _searchQuery,
            _date,
        ) { entries, catalog, profile, query, date ->
            val filteredCatalog = if (query.isBlank()) {
                catalog
            } else {
                catalog.filter { it.name.contains(query, ignoreCase = true) }
            }
            val totals = entries.fold(Macros.ZERO) { acc, e -> acc + e.macros }
            catalogIsEmptyNow = catalog.isEmpty()
            Quint(entries, filteredCatalog, profile, totals, date)
        }.onEach { (entries, filteredCatalog, profile, totals, date) ->
            _state.value = _state.value.copy(
                date = date,
                entries = entries,
                catalog = filteredCatalog,
                catalogIsEmpty = catalogIsEmptyNow,
                profile = profile,
                dailyTotals = totals,
                productNamesById = _state.value.productNamesById.let { existing ->
                    // Keep a running name lookup so entries can render a product name
                    // even if the catalog list is currently filtered by search.
                    existing + filteredCatalog.associate { it.id to it.name }
                },
            )
        }.launchIn(viewModelScope)

        _date.flatMapLatest { endDate ->
            val referenceDate = LocalDate.parse(endDate)
            val monday = mondayOfWeek(referenceDate)
            val sunday = monday.plus(DatePeriod(days = 6))
            container.diaryRepository.observeDailyTotalsInRange(monday.toString(), sunday.toString())
                .map { dailyTotals -> monday to dailyTotals }
        }.onEach { (monday, dailyTotals) ->
            // Always emit exactly 7 points (Mon..Sun), even for days with no
            // entries yet, so the weekly bar always has all 7 segments.
            val totalsByDate = dailyTotals.associateBy { it.date }
            val points = (0..6).map { offset ->
                val date = monday.plus(DatePeriod(days = offset))
                val macros = totalsByDate[date.toString()]?.macros ?: Macros.ZERO
                StatsPoint(date.toString(), macros)
            }
            _state.value = _state.value.copy(weeklyPoints = points)
        }.launchIn(viewModelScope)

        // Live: editing a product's measures updates its catalog card at once.
        container.productRepository.observeAllUnits().onEach { units ->
            _state.value = _state.value.copy(unitsByProduct = units.groupBy { it.productId })
        }.launchIn(viewModelScope)
    }

    /** Monday of the ISO week containing [date] (Mon=start, per current agreement). */
    private fun mondayOfWeek(date: LocalDate): LocalDate {
        val isoDayNumber = date.dayOfWeek.ordinal // MONDAY=0 .. SUNDAY=6, see CalendarGrid.kt
        return date.minus(DatePeriod(days = isoDayNumber))
    }

    fun changeDate(newDate: String) {
        _date.value = newDate
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun addToDiary(productId: String, amount: DiaryAmount) {
        viewModelScope.launch {
            when (amount) {
                is DiaryAmount.Grams -> container.addDiaryEntryUseCase(
                    productId = productId,
                    entryDate = _date.value,
                    grams = amount.grams,
                )
                // The use case turns "count x measure" into grams itself
                // (e.g. 2 x 10 g = 20 g) and scales the per-100g macros.
                is DiaryAmount.Measure -> container.addDiaryEntryUseCase(
                    productId = productId,
                    entryDate = _date.value,
                    unitId = amount.unitId,
                    unitCount = amount.count,
                )
            }
        }
    }

    /** Long-press menu → «Удалить». Refuses (with an explanation) if the product is in use. */
    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            when (val result = container.deleteProductUseCase(productId)) {
                DeleteProductResult.Deleted -> Unit // the catalog Flow drops the card by itself
                is DeleteProductResult.InUse -> {
                    val reasons = buildList {
                        if (result.diaryEntries > 0) add("он есть в дневнике (записей: ${result.diaryEntries})")
                        if (result.usedInProducts.isNotEmpty()) {
                            add("он входит в состав: ${result.usedInProducts.joinToString(", ") { "«$it»" }}")
                        }
                    }
                    _state.value = _state.value.copy(
                        notice = "Продукт нельзя удалить: ${reasons.joinToString("; ")}.",
                    )
                }
            }
        }
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun removeFromDiary(entryId: String) {
        viewModelScope.launch {
            container.diaryRepository.deleteEntry(entryId)
        }
    }
}

private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component1() = a
private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component2() = b
private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component3() = c
private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component4() = d
private operator fun <A, B, C, D, E> Quint<A, B, C, D, E>.component5() = e
