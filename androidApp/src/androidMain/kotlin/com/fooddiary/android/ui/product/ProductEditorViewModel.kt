package com.fooddiary.android.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fooddiary.android.AppContainer
import com.fooddiary.domain.CalcMode
import com.fooddiary.domain.CompositionLine
import com.fooddiary.domain.Macros
import com.fooddiary.domain.Product
import com.fooddiary.domain.ProductSource
import com.fooddiary.domain.ProductUnit
import com.fooddiary.usecase.SaveProductResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * One row in the composition editor UI — references an ingredient product by
 * id + name for display. The amount can be typed either in grams or in one of
 * the ingredient's household measures, the same choice as when adding a
 * product to the diary. Only grams are stored in the database: on save,
 * "2 x ст. ложка (25 г)" becomes 50 g.
 */
data class CompositionLineUi(
    val lineId: String,
    val ingredientProductId: String,
    val ingredientName: String,
    /** The number as typed: grams if [measure] is null, otherwise a count of [measure]. */
    val amountText: String,
    /** The ingredient's saved household measures (empty = grams only). */
    val measures: List<ProductUnit> = emptyList(),
    /** Chosen measure; null = weight in grams. */
    val measure: ProductUnit? = null,
    /** True while the "Вес / мера" buttons are shown instead of the amount field. */
    val choosingUnit: Boolean = false,
) {
    /** Grams this line stands for, or null if the amount isn't a positive number yet. */
    fun gramsOrNull(): Double? {
        val number = amountText.toPositiveDoubleOrNull() ?: return null
        return measure?.let { number * it.gramsPerUnit } ?: number
    }
}

data class ProductUnitUi(
    val unitId: String,
    val unitName: String,
    val gramsPerUnitText: String,
)

data class ProductEditorUiState(
    val productId: String = "",
    val name: String = "",
    val calcMode: CalcMode = CalcMode.MANUAL,
    // Manual fields
    val manualKcal: String = "",
    val manualProtein: String = "",
    val manualFat: String = "",
    val manualCarb: String = "",
    // Computed fields
    val yieldGramsText: String = "",
    /**
     * false: the yield follows the sum of the ingredients automatically;
     * true: the user typed their own value (e.g. water boiled off), which is
     * kept until they press «Взять сумму».
     */
    val yieldIsManual: Boolean = false,
    val compositionLines: List<CompositionLineUi> = emptyList(),
    // Units
    val units: List<ProductUnitUi> = emptyList(),
    // Ingredient search (for adding a composition line)
    val ingredientSearchQuery: String = "",
    val ingredientSearchResults: List<Product> = emptyList(),
    // Result / validation
    val errorMessage: String? = null,
    val saved: Boolean = false,
    val isEditingExisting: Boolean = false,
    /** Kept as loaded, so editing e.g. a scanned product doesn't relabel it as USER-made. */
    val source: ProductSource = ProductSource.USER,
)

@OptIn(ExperimentalUuidApi::class)
class ProductEditorViewModel(
    private val container: AppContainer,
    existingProductId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProductEditorUiState(
            productId = existingProductId ?: Uuid.random().toString(),
            isEditingExisting = existingProductId != null,
        ),
    )
    val state: StateFlow<ProductEditorUiState> = _state

    init {
        if (existingProductId != null) loadExisting(existingProductId)
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            val product = container.productRepository.getById(id) ?: return@launch
            val composition = container.productRepository.getComposition(id)
            val units = container.productRepository.getUnits(id)

            val compositionUi = composition.map { line ->
                val ingredient = container.productRepository.getById(line.ingredientProductId)
                CompositionLineUi(
                    lineId = line.id,
                    ingredientProductId = line.ingredientProductId,
                    ingredientName = ingredient?.name ?: "?",
                    // Only grams are stored, so saved lines always reopen in grams;
                    // the measures are loaded so the user can switch if they want.
                    amountText = line.amountGrams.toCleanString(),
                    measures = container.productRepository.getUnits(line.ingredientProductId),
                )
            }

            _state.value = _state.value.copy(
                name = product.name,
                calcMode = product.calcMode,
                manualKcal = product.macrosPer100.kcal.toCleanString(),
                manualProtein = product.macrosPer100.protein.toCleanString(),
                manualFat = product.macrosPer100.fat.toCleanString(),
                manualCarb = product.macrosPer100.carb.toCleanString(),
                yieldGramsText = product.yieldGrams?.toCleanString() ?: "",
                // A saved yield that differs from the ingredients' sum was typed by
                // hand (or saved before auto-sum existed) — keep it as manual.
                yieldIsManual = product.yieldGrams?.let { saved ->
                    kotlin.math.abs(saved - composition.sumOf { it.amountGrams }) > 0.05
                } ?: false,
                compositionLines = compositionUi,
                units = units.map { ProductUnitUi(it.id, it.unitName, it.gramsPerUnit.toCleanString()) },
                source = product.source,
            )
        }
    }

    fun onNameChanged(name: String) {
        _state.value = _state.value.copy(name = name)
    }

    fun onCalcModeChanged(mode: CalcMode) {
        _state.value = withAutoYield(_state.value.copy(calcMode = mode))
    }

    fun onManualMacroChanged(kcal: String? = null, protein: String? = null, fat: String? = null, carb: String? = null) {
        _state.value = _state.value.copy(
            manualKcal = kcal ?: _state.value.manualKcal,
            manualProtein = protein ?: _state.value.manualProtein,
            manualFat = fat ?: _state.value.manualFat,
            manualCarb = carb ?: _state.value.manualCarb,
        )
    }

    fun onYieldGramsChanged(text: String) {
        _state.value = _state.value.copy(yieldGramsText = text, yieldIsManual = true)
    }

    /** «Взять сумму»: drop the typed yield and follow the ingredients again. */
    fun useIngredientsSumAsYield() {
        _state.value = withAutoYield(_state.value.copy(yieldIsManual = false))
    }

    /** If the yield is automatic, set it to the current sum of the ingredients' grams. */
    private fun withAutoYield(s: ProductEditorUiState): ProductEditorUiState {
        if (s.yieldIsManual) return s
        val sum = s.ingredientsTotalGrams()
        return s.copy(yieldGramsText = if (sum > 0.0) sum.roundToOneDecimal().toCleanString() else "")
    }

    fun onIngredientSearchQueryChanged(query: String) {
        _state.value = _state.value.copy(ingredientSearchQuery = query)
        viewModelScope.launch {
            // Exclude the product currently being edited from its own ingredient search
            // (a fast first line of defense against self-reference; the real cycle
            // check happens in SaveProductUseCase against the full graph).
            val results = container.productRepository.search(query)
                .filter { it.id != _state.value.productId }
            _state.value = _state.value.copy(ingredientSearchResults = results)
        }
    }

    /**
     * Adds an ingredient line. Like the diary's "Добавить": if the ingredient
     * has household measures, the line first shows "Вес" / measure buttons;
     * without measures it goes straight to the grams field.
     * [onAdded] gets the new line's id (the screen focuses its field once it appears).
     */
    fun addCompositionLine(ingredient: Product, onAdded: (lineId: String) -> Unit = {}) {
        _state.value = _state.value.copy(ingredientSearchQuery = "", ingredientSearchResults = emptyList())
        viewModelScope.launch {
            val measures = container.productRepository.getUnits(ingredient.id)
            val newLine = CompositionLineUi(
                lineId = Uuid.random().toString(),
                ingredientProductId = ingredient.id,
                ingredientName = ingredient.name,
                amountText = "",
                measures = measures,
                choosingUnit = measures.isNotEmpty(),
            )
            _state.value = withAutoYield(_state.value.copy(compositionLines = _state.value.compositionLines + newLine))
            onAdded(newLine.lineId)
        }
    }

    fun updateCompositionLineAmount(lineId: String, amountText: String) {
        updateLine(lineId) { it.copy(amountText = amountText) }
    }

    /** Show the "Вес / мера" buttons again for this line. */
    fun startChoosingLineUnit(lineId: String) {
        updateLine(lineId) { it.copy(choosingUnit = true) }
    }

    /** [measure] == null means "Вес" (grams). Same presets as the diary: 1 measure, empty grams. */
    fun chooseLineUnit(lineId: String, measure: ProductUnit?) {
        updateLine(lineId) {
            it.copy(
                measure = measure,
                choosingUnit = false,
                amountText = if (measure == null) "" else "1",
            )
        }
    }

    private fun updateLine(lineId: String, change: (CompositionLineUi) -> CompositionLineUi) {
        _state.value = withAutoYield(
            _state.value.copy(
                compositionLines = _state.value.compositionLines.map {
                    if (it.lineId == lineId) change(it) else it
                },
            ),
        )
    }

    fun removeCompositionLine(lineId: String) {
        _state.value = withAutoYield(
            _state.value.copy(
                compositionLines = _state.value.compositionLines.filterNot { it.lineId == lineId },
            ),
        )
    }

    /** Adds an empty measure row and returns its id (the screen focuses its name field). */
    fun addUnit(): String {
        val id = Uuid.random().toString()
        _state.value = _state.value.copy(
            units = _state.value.units + ProductUnitUi(id, "", ""),
        )
        return id
    }

    fun updateUnit(unitId: String, name: String? = null, gramsPerUnit: String? = null) {
        _state.value = _state.value.copy(
            units = _state.value.units.map {
                if (it.unitId == unitId) {
                    it.copy(
                        unitName = name ?: it.unitName,
                        gramsPerUnitText = gramsPerUnit ?: it.gramsPerUnitText,
                    )
                } else it
            },
        )
    }

    fun removeUnit(unitId: String) {
        _state.value = _state.value.copy(units = _state.value.units.filterNot { it.unitId == unitId })
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.value = s.copy(errorMessage = "Введите название продукта")
            return
        }

        // A measure row is either completely empty (ignored) or must have both a
        // name and a weight > 0. Otherwise it would be silently lost on save.
        val incompleteMeasure = s.units.firstOrNull { u ->
            val anythingTyped = u.unitName.isNotBlank() || u.gramsPerUnitText.isNotBlank()
            val complete = u.unitName.isNotBlank() && u.gramsPerUnitText.toPositiveDoubleOrNull() != null
            anythingTyped && !complete
        }
        if (incompleteMeasure != null) {
            _state.value = s.copy(
                errorMessage = "Бытовая мера заполнена не полностью: укажите и название, и вес в граммах (больше нуля), либо удалите строку",
            )
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()

            val macros: Macros
            val yieldGrams: Double?
            val lines: List<CompositionLine>

            if (s.calcMode == CalcMode.MANUAL) {
                val kcal = s.manualKcal.toDoubleOrNull()
                val protein = s.manualProtein.toDoubleOrNull()
                val fat = s.manualFat.toDoubleOrNull()
                val carb = s.manualCarb.toDoubleOrNull()
                if (kcal == null || protein == null || fat == null || carb == null) {
                    _state.value = s.copy(errorMessage = "Заполните все поля КБЖУ числами")
                    return@launch
                }
                macros = Macros(kcal, protein, fat, carb)
                yieldGrams = null
                lines = emptyList()
            } else {
                val yield = s.yieldGramsText.replace(',', '.').toDoubleOrNull()
                if (yield == null || yield <= 0.0) {
                    _state.value = s.copy(errorMessage = "Укажите итоговый выход блюда в граммах")
                    return@launch
                }
                if (s.compositionLines.isEmpty()) {
                    _state.value = s.copy(errorMessage = "Добавьте хотя бы один ингредиент")
                    return@launch
                }
                val parsedLines = mutableListOf<CompositionLine>()
                for (line in s.compositionLines) {
                    if (line.choosingUnit) {
                        _state.value = s.copy(errorMessage = "Выберите, как указать количество «${line.ingredientName}»: вес или мера")
                        return@launch
                    }
                    val amount = line.gramsOrNull()
                    if (amount == null) {
                        _state.value = s.copy(errorMessage = "Укажите корректный вес для «${line.ingredientName}»")
                        return@launch
                    }
                    parsedLines += CompositionLine(
                        id = line.lineId,
                        parentProductId = s.productId,
                        ingredientProductId = line.ingredientProductId,
                        amountGrams = amount,
                    )
                }
                yieldGrams = yield
                lines = parsedLines
                macros = Macros.ZERO // placeholder; SaveProductUseCase recomputes this from composition
            }

            val product = Product(
                id = s.productId,
                name = s.name.trim(),
                calcMode = s.calcMode,
                macrosPer100 = macros,
                yieldGrams = yieldGrams,
                source = s.source,
                createdAt = 0L, // ignored by SaveProductUseCase — it looks up the real value itself
                updatedAt = now,
            )

            when (val result = container.saveProductUseCase(product, lines)) {
                is SaveProductResult.Success -> {
                    saveUnits(s)
                    _state.value = _state.value.copy(saved = true, errorMessage = null)
                }
                is SaveProductResult.CycleDetected -> {
                    _state.value = s.copy(
                        errorMessage = "Этот ингредиент уже зависит от редактируемого продукта — так нельзя, получится замкнутый круг.",
                    )
                }
                SaveProductResult.IncompleteComposition -> {
                    _state.value = s.copy(errorMessage = "Укажите выход блюда и хотя бы один ингредиент")
                }
            }
        }
    }

    private suspend fun saveUnits(s: ProductEditorUiState) {
        val kept = s.units.mapNotNull { unit ->
            val grams = unit.gramsPerUnitText.toPositiveDoubleOrNull()
            if (unit.unitName.isNotBlank() && grams != null) {
                ProductUnit(
                    id = unit.unitId,
                    productId = s.productId,
                    unitName = unit.unitName.trim(),
                    gramsPerUnit = grams,
                )
            } else {
                null
            }
        }
        kept.forEach { container.productRepository.upsertUnit(it) }

        // Measures the user removed in the form must disappear from the database
        // too; otherwise they would keep showing up on the product card.
        val keptIds = kept.map { it.id }.toSet()
        container.productRepository.getUnits(s.productId)
            .filter { it.id !in keptIds }
            .forEach { container.productRepository.deleteUnit(it.id) }
    }
}

/** Sum of the grams of all lines that already have a valid amount. */
fun ProductEditorUiState.ingredientsTotalGrams(): Double =
    compositionLines.sumOf { if (it.choosingUnit) 0.0 else it.gramsOrNull() ?: 0.0 }

private fun Double.roundToOneDecimal(): Double = kotlin.math.round(this * 10) / 10

private fun Double.toCleanString(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()

/** Parses a positive number, accepting a comma as the decimal separator. */
private fun String.toPositiveDoubleOrNull(): Double? =
    replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
