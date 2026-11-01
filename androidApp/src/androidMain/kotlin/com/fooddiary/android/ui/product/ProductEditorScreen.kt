package com.fooddiary.android.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fooddiary.android.AppContainer
import com.fooddiary.domain.CalcMode
import com.fooddiary.domain.Product
import com.fooddiary.domain.ProductUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditorScreen(
    container: AppContainer,
    existingProductId: String?,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    sessionKey: Int = 0,
) {
    val viewModel: ProductEditorViewModel = viewModel(
        key = "product-editor-${existingProductId ?: "new"}-$sessionKey",
        factory = ProductEditorViewModelFactory(container, existingProductId),
    )
    val state by viewModel.state.collectAsState()

    if (state.saved) {
        onSaved()
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (state.isEditingExisting) "Редактировать продукт" else "Новый продукт") },
        )

        // weight(1f) bounds this column to the space left under the app bar;
        // together with windowSoftInputMode=adjustResize (manifest) the visible
        // area shrinks when the keyboard opens, so the form stays scrollable
        // instead of hiding fields behind the keyboard.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChanged,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.calcMode == CalcMode.MANUAL,
                    onClick = { viewModel.onCalcModeChanged(CalcMode.MANUAL) },
                    label = { Text("КБЖУ вручную") },
                )
                FilterChip(
                    selected = state.calcMode == CalcMode.COMPUTED,
                    onClick = { viewModel.onCalcModeChanged(CalcMode.COMPUTED) },
                    label = { Text("Считать из состава") },
                )
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            if (state.calcMode == CalcMode.MANUAL) {
                ManualMacrosSection(state = state, viewModel = viewModel)
            } else {
                ComputedCompositionSection(state = state, viewModel = viewModel)
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))
            UnitsSection(state = state, viewModel = viewModel)

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }

            Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::save) { Text("Сохранить") }
                TextButton(onClick = onCancel) { Text("Отмена") }
            }
        }
    }
}

@Composable
private fun ManualMacrosSection(state: ProductEditorUiState, viewModel: ProductEditorViewModel) {
    Text("КБЖУ на 100 г", fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = state.manualKcal,
        onValueChange = { viewModel.onManualMacroChanged(kcal = it) },
        label = { Text("Калории") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
    )
    OutlinedTextField(
        value = state.manualProtein,
        onValueChange = { viewModel.onManualMacroChanged(protein = it) },
        label = { Text("Белки, г") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
    )
    OutlinedTextField(
        value = state.manualFat,
        onValueChange = { viewModel.onManualMacroChanged(fat = it) },
        label = { Text("Жиры, г") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
    )
    OutlinedTextField(
        value = state.manualCarb,
        onValueChange = { viewModel.onManualMacroChanged(carb = it) },
        label = { Text("Углеводы, г") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
    )
}

@Composable
private fun ComputedCompositionSection(state: ProductEditorUiState, viewModel: ProductEditorViewModel) {
    Text("Состав блюда", fontWeight = FontWeight.Bold)

    OutlinedTextField(
        value = state.yieldGramsText,
        onValueChange = viewModel::onYieldGramsChanged,
        label = { Text("Итоговый выход блюда, г") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    if (state.yieldIsManual) {
        // The user's own value: show the sum for comparison and a way back to it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Сумма ингредиентов: ${state.ingredientsTotalGrams().toShortString()} г",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::useIngredientsSumAsYield) { Text("Взять сумму") }
        }
    } else {
        Text(
            "Считается по сумме ингредиентов. Если при готовке вес изменился (например, выкипела вода) — впишите свой.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    Text(
        "КБЖУ на 100г будет рассчитано автоматически при сохранении.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )

    // Id of a line whose amount field should get the cursor as soon as it
    // shows up (a just-added ingredient, or one whose unit was just chosen).
    var focusLineId by remember { mutableStateOf<String?>(null) }

    state.compositionLines.forEach { line ->
        CompositionLineRow(
            line = line,
            requestFocus = line.lineId == focusLineId,
            onFocusHandled = { focusLineId = null },
            onAmountChanged = { viewModel.updateCompositionLineAmount(line.lineId, it) },
            onChooseUnit = { measure ->
                viewModel.chooseLineUnit(line.lineId, measure)
                focusLineId = line.lineId
            },
            onChangeUnit = { viewModel.startChoosingLineUnit(line.lineId) },
            onRemove = { viewModel.removeCompositionLine(line.lineId) },
        )
    }

    Text("Добавить ингредиент", modifier = Modifier.padding(top = 16.dp))
    OutlinedTextField(
        value = state.ingredientSearchQuery,
        onValueChange = viewModel::onIngredientSearchQueryChanged,
        label = { Text("Поиск продукта") },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        singleLine = true,
        trailingIcon = {
            if (state.ingredientSearchQuery.isNotEmpty()) {
                IconButton(onClick = { viewModel.onIngredientSearchQueryChanged("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Очистить поиск")
                }
            }
        },
    )
    if (state.ingredientSearchResults.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column {
                state.ingredientSearchResults.forEach { product: Product ->
                    TextButton(
                        onClick = { viewModel.addCompositionLine(product) { id -> focusLineId = id } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Добавить «${product.name}»") }
                }
            }
        }
    }
}

/**
 * One ingredient of a composed product:
 *   [name ................ ✕]
 *   then either the "Вес / мера" buttons (same as on a diary product card),
 *   or [amount field (label = unit)] [unit switch], plus "= N г" for measures.
 */
@Composable
private fun CompositionLineRow(
    line: CompositionLineUi,
    requestFocus: Boolean,
    onFocusHandled: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onChooseUnit: (ProductUnit?) -> Unit,
    onChangeUnit: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line.ingredientName,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Убрать ингредиент", modifier = Modifier.size(20.dp))
                }
            }

            if (line.choosingUnit) {
                UnitChoice(measures = line.measures, onChoose = onChooseUnit)
            } else {
                val focusRequester = remember(line.lineId) { FocusRequester() }
                if (requestFocus) {
                    LaunchedEffect(line.lineId, line.measure) {
                        runCatching { focusRequester.requestFocus() }
                        onFocusHandled()
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = line.amountText,
                        onValueChange = onAmountChanged,
                        label = {
                            Text(
                                line.measure?.unitName ?: "Вес, г",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    if (line.measures.isNotEmpty()) {
                        // Switch between grams and the ingredient's measures.
                        OutlinedButton(
                            onClick = onChangeUnit,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        ) { Text("Ед. изм.", style = MaterialTheme.typography.labelMedium) }
                    }
                }
                if (line.measure != null) {
                    val grams = line.gramsOrNull()
                    Text(
                        if (grams != null) "= ${grams.toShortString()} г" else "1 ${line.measure.unitName} = ${line.measure.gramsPerUnit.toShortString()} г",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                    )
                }
            }
        }
    }
}

/** "Вес" + the ingredient's measures; one measure → two halves, several → a column (as in the diary). */
@Composable
private fun UnitChoice(measures: List<ProductUnit>, onChoose: (ProductUnit?) -> Unit) {
    if (measures.size <= 1) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            UnitChoiceButton("Вес", { onChoose(null) }, Modifier.weight(1f))
            measures.forEach { m -> UnitChoiceButton(m.unitName, { onChoose(m) }, Modifier.weight(1f)) }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            UnitChoiceButton("Вес", { onChoose(null) }, Modifier.fillMaxWidth())
            measures.forEach { m -> UnitChoiceButton(m.unitName, { onChoose(m) }, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun UnitChoiceButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** 12.0 -> "12", 12.5 -> "12.5", 33.3333 -> "33.3". */
private fun Double.toShortString(): String {
    val rounded = kotlin.math.round(this * 10) / 10
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}

@Composable
private fun UnitsSection(state: ProductEditorUiState, viewModel: ProductEditorViewModel) {
    Text("Бытовые меры (необязательно)", fontWeight = FontWeight.Bold)
    Text(
        "Например: 1 шт = 55 г",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    // Id of a just-added measure whose name field should get the cursor (and
    // the keyboard) as soon as it appears, so the user can type right away.
    var focusUnitId by remember { mutableStateOf<String?>(null) }

    state.units.forEach { unit ->
        val nameFocus = remember(unit.unitId) { FocusRequester() }
        if (unit.unitId == focusUnitId) {
            LaunchedEffect(unit.unitId) {
                runCatching { nameFocus.requestFocus() }
                focusUnitId = null
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = unit.unitName,
                onValueChange = { viewModel.updateUnit(unit.unitId, name = it) },
                label = { Text("Название (шт, стакан...)") },
                modifier = Modifier.weight(1f).focusRequester(nameFocus),
                singleLine = true,
            )
            OutlinedTextField(
                value = unit.gramsPerUnitText,
                onValueChange = { viewModel.updateUnit(unit.unitId, gramsPerUnit = it) },
                label = { Text("грамм") },
                modifier = Modifier.weight(0.6f),
                singleLine = true,
            )
            TextButton(onClick = { viewModel.removeUnit(unit.unitId) }) { Text("✕") }
        }
    }
    TextButton(onClick = { focusUnitId = viewModel.addUnit() }, modifier = Modifier.padding(top = 4.dp)) {
        Text("+ Добавить меру")
    }
}
