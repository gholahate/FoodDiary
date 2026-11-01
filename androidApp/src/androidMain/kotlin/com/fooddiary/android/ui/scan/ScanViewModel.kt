package com.fooddiary.android.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fooddiary.android.AppContainer
import com.fooddiary.android.data.OffProduct
import com.fooddiary.android.data.extractProductBarcode
import com.fooddiary.domain.CalcMode
import com.fooddiary.domain.Product
import com.fooddiary.domain.ProductSource
import com.fooddiary.domain.ProductUnit
import com.fooddiary.usecase.SaveProductResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One found product as shown on the results screen. */
data class ScanResultItem(
    val product: OffProduct,
    /** A product with exactly this name is already in the user's base. */
    val alreadyInBase: Boolean,
)

sealed interface ScanUiState {
    /** The camera scanner is (about to be) on screen. [attempt] changes to restart it. */
    data class Scanning(val attempt: Int) : ScanUiState

    data class Searching(val barcode: String) : ScanUiState

    data class Found(val barcode: String, val items: List<ScanResultItem>, val savingIndex: Int? = null) : ScanUiState

    data class NotFound(val barcode: String) : ScanUiState

    /** The code was read, but it isn't a product barcode (e.g. a QR with a website). */
    data class NotAProductCode(val rawValue: String) : ScanUiState

    /** [barcode] != null → "Повторить" repeats the search; null → it restarts the scanner. */
    data class Error(val message: String, val barcode: String?) : ScanUiState

    /** A product was saved to the base; the screen closes with its name. */
    data class Added(val productName: String) : ScanUiState
}

/**
 * Scan → look up in Open Food Facts → show results → "Добавить" saves the
 * chosen product into the user's own product base (КБЖУ per 100 g, plus
 * "Упаковка"/"Порция" measures when OFF knows their weight).
 */
@OptIn(ExperimentalUuidApi::class)
class ScanViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Scanning(attempt = 0))
    val state: StateFlow<ScanUiState> = _state

    private var attempts = 0

    fun rescan() {
        attempts++
        _state.value = ScanUiState.Scanning(attempts)
    }

    fun onScanned(rawValue: String) {
        val barcode = extractProductBarcode(rawValue)
        if (barcode == null) {
            _state.value = ScanUiState.NotAProductCode(rawValue)
            return
        }
        search(barcode)
    }

    fun onScanFailed(message: String) {
        _state.value = ScanUiState.Error(message, barcode = null)
    }

    fun retry() {
        when (val s = _state.value) {
            is ScanUiState.Error -> if (s.barcode != null) search(s.barcode) else rescan()
            else -> rescan()
        }
    }

    private fun search(barcode: String) {
        _state.value = ScanUiState.Searching(barcode)
        viewModelScope.launch {
            try {
                val found = container.openFoodFacts.findByBarcode(barcode)
                if (found.isEmpty()) {
                    _state.value = ScanUiState.NotFound(barcode)
                } else {
                    val existingNames = container.productRepository.search("")
                        .map { it.name.trim().lowercase() }
                        .toSet()
                    _state.value = ScanUiState.Found(
                        barcode = barcode,
                        items = found.map { ScanResultItem(it, it.displayName.trim().lowercase() in existingNames) },
                    )
                }
            } catch (e: IOException) {
                _state.value = ScanUiState.Error(
                    "Не удалось связаться с базой Open Food Facts. Проверьте интернет и попробуйте ещё раз.",
                    barcode = barcode,
                )
            } catch (e: Exception) {
                _state.value = ScanUiState.Error(
                    "Ответ Open Food Facts не удалось разобрать (${e.javaClass.simpleName}).",
                    barcode = barcode,
                )
            }
        }
    }

    fun addToBase(index: Int) {
        val found = _state.value as? ScanUiState.Found ?: return
        if (found.savingIndex != null) return
        val item = found.items.getOrNull(index)?.product ?: return
        _state.value = found.copy(savingIndex = index)

        viewModelScope.launch {
            val productId = Uuid.random().toString()
            val product = Product(
                id = productId,
                name = item.displayName,
                calcMode = CalcMode.MANUAL,
                macrosPer100 = item.macrosPer100,
                yieldGrams = null,
                source = ProductSource.OPEN_FOOD_FACTS,
                createdAt = 0L, // set by SaveProductUseCase
                updatedAt = 0L,
            )
            when (container.saveProductUseCase(product, emptyList())) {
                is SaveProductResult.Success -> {
                    item.packageGrams?.let { grams ->
                        container.productRepository.upsertUnit(
                            ProductUnit(Uuid.random().toString(), productId, "Упаковка", grams),
                        )
                    }
                    item.servingGrams?.let { grams ->
                        container.productRepository.upsertUnit(
                            ProductUnit(Uuid.random().toString(), productId, "Порция", grams),
                        )
                    }
                    _state.value = ScanUiState.Added(item.displayName)
                }
                else -> {
                    _state.value = ScanUiState.Error("Не удалось сохранить продукт в базу.", barcode = found.barcode)
                }
            }
        }
    }
}

class ScanViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == ScanViewModel::class.java)
        return ScanViewModel(container) as T
    }
}
