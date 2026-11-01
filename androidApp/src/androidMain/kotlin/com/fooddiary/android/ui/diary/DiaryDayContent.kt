package com.fooddiary.android.ui.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.fooddiary.android.ui.common.rememberKeyboardVisible
import com.fooddiary.android.ui.theme.StatusColors
import com.fooddiary.domain.DiaryEntry
import com.fooddiary.domain.Macros
import com.fooddiary.domain.Product
import com.fooddiary.domain.ProductUnit
import com.fooddiary.domain.UserProfile
import kotlin.math.roundToInt

/**
 * The full "one day of diary" layout: search bar, diary/catalog split panels,
 * daily totals with per-macro progress bars, and a trailing 7-day mini chart.
 * [diaryPanelTitle] lets callers say "today" vs "this day" without duplicating
 * the whole layout (Today screen vs History screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDayContent(
    state: DiaryDayUiState,
    diaryPanelTitle: String,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onAddToDiary: (productId: String, amount: DiaryAmount) -> Unit,
    onRemoveFromDiary: (entryId: String) -> Unit,
    onEditProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
    showWeeklyChart: Boolean = true,
    onAddProduct: (() -> Unit)? = null,
    onScanProduct: (() -> Unit)? = null,
    onDeleteProduct: (productId: String) -> Unit = {},
    onDismissNotice: () -> Unit = {},
) {
    state.notice?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissNotice,
            confirmButton = { TextButton(onClick = onDismissNotice) { Text("Понятно") } },
            text = { Text(message) },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                label = { Text("Поиск продукта") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Очистить поиск")
                        }
                    }
                },
            )
            if (onAddProduct != null) {
                // "+" opens the add menu: create by hand, or scan a barcode.
                var addMenuOpen by remember { mutableStateOf(false) }
                Box {
                    FilledIconButton(
                        onClick = {
                            if (onScanProduct == null) onAddProduct() else addMenuOpen = true
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Добавить продукт в базу")
                    }
                    DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Создать продукт") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                addMenuOpen = false
                                onAddProduct()
                            },
                        )
                        if (onScanProduct != null) {
                            DropdownMenuItem(
                                text = { Text("Сканировать штрихкод") },
                                leadingIcon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                                onClick = {
                                    addMenuOpen = false
                                    onScanProduct()
                                },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DiaryPanel(
                title = diaryPanelTitle,
                entries = state.entries,
                productNamesById = state.productNamesById,
                onRemove = onRemoveFromDiary,
                modifier = Modifier.weight(1f),
            )
            CatalogPanel(
                products = state.catalog,
                unitsByProduct = state.unitsByProduct,
                catalogIsEmpty = state.catalogIsEmpty,
                searchQuery = searchQuery,
                canAddHere = onAddProduct != null,
                onAdd = onAddToDiary,
                onEdit = onEditProduct,
                onDelete = onDeleteProduct,
                modifier = Modifier.weight(1f),
            )
        }

        // While the keyboard is open the totals and the weekly bar are hidden
        // (the keyboard "covers" them) so the product list gets the whole free
        // area, and the field being typed in always stays visible above the keyboard.
        val keyboardVisible by rememberKeyboardVisible()
        if (!keyboardVisible) {
            Divider()
            DailyTotalsSection(totals = state.dailyTotals, profile = state.profile)
            if (showWeeklyChart) {
                WeeklyMiniChart(
                    points = state.weeklyPoints.map { StatsPointForChart(it.label, it.macros.kcal) },
                    targetKcal = state.profile?.targetKcal,
                )
            }
        }
    }
}

@Composable
private fun DiaryPanel(
    title: String,
    entries: List<DiaryEntry>,
    productNamesById: Map<String, String>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
        if (entries.isEmpty()) {
            Text(
                "Пока ничего не добавлено",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(4.dp),
            )
        }
        LazyColumn {
            items(entries, key = { it.id }) { entry ->
                // Same layout as a product card in "База продуктов": the name
                // takes 3/4 of the top row and the icon sits in the right 1/4
                // (a cross here, where the catalog card has the pencil).
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                productNamesById[entry.productId] ?: entry.productId,
                                modifier = Modifier.weight(3f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                IconButton(onClick = { onRemove(entry.id) }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Удалить из учтённого",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            "${entry.amountGrams.roundToInt()} г · ${entry.macros.kcal.roundToInt()} ккал",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/** What the user is currently doing on one product card. */
private sealed interface AddStage {
    /** Normal state: only the "Добавить" button is shown. */
    data object Idle : AddStage

    /** "Добавить" was pressed and the product has measures: choose Вес or a measure. */
    data object ChoosingMode : AddStage

    /** Typing the amount. [measure] == null means plain weight in grams. */
    data class Entering(val measure: ProductUnit?) : AddStage
}

@Composable
private fun CatalogPanel(
    products: List<Product>,
    unitsByProduct: Map<String, List<ProductUnit>>,
    catalogIsEmpty: Boolean,
    searchQuery: String,
    canAddHere: Boolean,
    onAdd: (productId: String, amount: DiaryAmount) -> Unit,
    onEdit: (productId: String) -> Unit,
    onDelete: (productId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text("База продуктов", fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
        when {
            // First launch / everything deleted: explain how to start.
            catalogIsEmpty -> EmptyBaseHint(canAddHere)
            products.isEmpty() && searchQuery.isNotBlank() -> Text(
                "Ничего не найдено по «$searchQuery»",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(4.dp),
            )
            else -> LazyColumn {
                items(products, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        measures = unitsByProduct[product.id].orEmpty(),
                        onAdd = { amount -> onAdd(product.id, amount) },
                        onEdit = { onEdit(product.id) },
                        onDelete = { onDelete(product.id) },
                    )
                }
            }
        }
    }
}

/** Shown instead of the list while the product base is empty. */
@Composable
private fun EmptyBaseHint(canAddHere: Boolean) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("База пока пуста", fontWeight = FontWeight.Bold)
            Text(
                if (canAddHere) {
                    "Сначала добавьте продукты: нажмите «+» рядом с поиском → «Создать продукт» или «Сканировать штрихкод»."
                } else {
                    "Сначала добавьте продукты на вкладке «Сегодня»: «+» рядом с поиском."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Пример: «Гречка варёная» — на 100 г: 110 ккал, Б 4, Ж 1, У 21. Значения есть на упаковке.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductCard(
    product: Product,
    measures: List<ProductUnit>,
    onAdd: (DiaryAmount) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var stage by remember { mutableStateOf<AddStage>(AddStage.Idle) }
    // Long press on the card → menu (Изменить / Удалить).
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить продукт?") },
            text = { Text("«${product.name}» будет удалён из базы вместе с его мерами.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }

    Box {
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text("Изменить") },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = {
                menuOpen = false
                onEdit()
            },
        )
        DropdownMenuItem(
            text = { Text("Удалить") },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = {
                menuOpen = false
                confirmDelete = true
            },
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
            ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 1) Name takes 3/4 of the row, the pencil gets the remaining 1/4.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    product.name,
                    modifier = Modifier.weight(3f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Редактировать продукт",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // 2) Info line, unchanged: kcal per 100 g.
            Text(
                "${product.macrosPer100.kcal.roundToInt()} ккал / 100г",
                style = MaterialTheme.typography.bodySmall,
            )

            // 3-5) Action row. Same place, three states.
            Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                when (val current = stage) {
                    AddStage.Idle -> FilledTonalButton(
                        onClick = {
                            // No measures saved for this product -> straight to weight.
                            stage = if (measures.isEmpty()) AddStage.Entering(null) else AddStage.ChoosingMode
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Добавить", maxLines = 1)
                    }

                    AddStage.ChoosingMode -> ModeChoice(
                        measures = measures,
                        onWeight = { stage = AddStage.Entering(null) },
                        onMeasure = { stage = AddStage.Entering(it) },
                    )

                    is AddStage.Entering -> {
                        val measure = current.measure
                        AmountEntryRow(
                            fieldLabel = measure?.unitName ?: "Вес, г",
                            initialText = if (measure == null) "100" else "1",
                            onConfirm = { number ->
                                onAdd(
                                    if (measure == null) {
                                        DiaryAmount.Grams(number)
                                    } else {
                                        DiaryAmount.Measure(unitId = measure.id, count = number)
                                    },
                                )
                                stage = AddStage.Idle
                            },
                            onCancel = { stage = AddStage.Idle },
                        )
                    }
                }
            }
        }
    }
    } // Box (menu anchor)
}

/**
 * "Вес" plus the product's saved measure(s). With one measure this is exactly
 * two buttons, half the row each. With several measures a side-by-side row
 * would be too cramped for the names, so they stack, one button per line.
 */
@Composable
private fun ModeChoice(
    measures: List<ProductUnit>,
    onWeight: () -> Unit,
    onMeasure: (ProductUnit) -> Unit,
) {
    if (measures.size <= 1) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ModeButton("Вес", onWeight, Modifier.weight(1f))
            measures.forEach { measure ->
                ModeButton(measure.unitName, { onMeasure(measure) }, Modifier.weight(1f))
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ModeButton("Вес", onWeight, Modifier.fillMaxWidth())
            measures.forEach { measure ->
                ModeButton(measure.unitName, { onMeasure(measure) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ModeButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

/**
 * One row: [value field] [confirm] [cancel]. The field is focused right away
 * with its preset value selected, so typing replaces it without having to
 * delete anything first. A comma is accepted as the decimal separator.
 */
@Composable
private fun AmountEntryRow(
    fieldLabel: String,
    initialText: String,
    onConfirm: (Double) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Harmless if the field isn't attached yet; the user can still tap it.
        runCatching { focusRequester.requestFocus() }
    }

    fun confirm() {
        val number = value.text.replace(',', '.').toDoubleOrNull()
        if (number != null && number > 0.0) onConfirm(number)
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(fieldLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        IconButton(onClick = { confirm() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Check, contentDescription = "Добавить", modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Отмена", modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DailyTotalsSection(totals: Macros, profile: UserProfile?) {
    // Top padding is deliberately small (4dp, was 12dp): it sets the gap between
    // the divider and the "Итог за день" label. Sides and bottom are unchanged.
    // Bottom 6dp (was 12dp) + the weekly card's top 6dp (was 12dp): the gap
    // between the last goal bar and the weekly bar is half of what it was.
    Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp)) {
        Text("Итог за день", fontWeight = FontWeight.Bold)
        val hasAnyGoal = profile?.hasAnyGoal() == true
        if (!hasAnyGoal) {
            Text(
                "Цели не заданы — зайдите в «Профиль» и включите хотя бы один параметр (калории, белки, жиры или углеводы), чтобы видеть прогресс здесь.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        MacroRow("Калории", totals.kcal, profile?.targetKcal, "ккал")
        MacroRow("Белки", totals.protein, profile?.targetProtein, "г")
        MacroRow("Жиры", totals.fat, profile?.targetFat, "г")
        MacroRow("Углеводы", totals.carb, profile?.targetCarb, "г")
    }
}

@Composable
private fun MacroRow(label: String, value: Double, target: Double?, unit: String) {
    // Only rendered if the user is tracking this macro (target != null), per design doc §5.
    if (target == null) return
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$label: ${value.roundToInt()} / ${target.roundToInt()} $unit")
        val progress = (value / target).toFloat().coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
    }
}

@Composable
private fun WeeklyMiniChart(points: List<StatsPointForChart>, targetKcal: Double?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.85f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("Неделя (ккал)", fontWeight = FontWeight.Bold)

            if (targetKcal == null) {
                Text(
                    "Задайте цель по калориям в «Профиле», чтобы видеть эту шкалу.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                return@Card
            }

            WeeklySegmentedBar(
                points = points,
                targetKcal = targetKcal,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** One day's chart data: its date and its kcal total, kept together for clarity at call sites. */
data class StatsPointForChart(val date: String, val kcal: Double)

/**
 * A horizontal "health bar"-style gauge: one segment per day's calorie
 * target (Mon..Sun, 7 segments), plus an extra 8th purple segment when the
 * week's total overflow couldn't be redistributed anywhere (see
 * [computeWeeklyOverflowLayout]). Each segment can show two fills: orange
 * for overflow carried in from another day, and green (or solid red, for a
 * day that alone exceeds target) for that day's own consumption. This is a
 * purely visual redistribution — it never changes the underlying daily
 * totals shown elsewhere (Today's own total, the Charts tab, etc.).
 */
@Composable
private fun WeeklySegmentedBar(
    points: List<StatsPointForChart>,
    targetKcal: Double,
    modifier: Modifier = Modifier,
) {
    val barHeight = 28.dp
    val segmentGap = 5.dp

    val layout = remember(points, targetKcal) {
        computeWeeklyOverflowLayout(points.map { it.kcal }, targetKcal)
    }
    val hasOverflowSegment = layout.unplacedOverflowFraction > 0f

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(barHeight)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(segmentGap),
            ) {
                layout.days.forEach { day ->
                    DaySegmentBox(day = day, modifier = Modifier.weight(1f))
                }
                if (hasOverflowSegment) {
                    // Extra segment for overflow that didn't fit anywhere in
                    // the week even after backward+forward redistribution.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(4.dp),
                            ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(layout.unplacedOverflowFraction.coerceIn(0.04f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(StatusColors.unplaced), // purple
                        )
                    }
                }
            }
        }

        if (hasOverflowSegment) {
            // The purple segment is too narrow to fit a number inside it, so
            // the exact leftover amount is shown as a caption below the bar.
            Text(
                "Не поместилось в неделю: +${layout.unplacedOverflowKcal.roundToInt()} ккал",
                style = MaterialTheme.typography.labelSmall,
                color = StatusColors.unplacedText(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DaySegmentBox(day: DaySegmentLayout, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                shape = RoundedCornerShape(4.dp),
            ),
    ) {
        if (day.isSelfOverGoal) {
            // This day alone exceeds target: solid red, regardless of exact
            // magnitude — carry never enters or leaves visually from here.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(StatusColors.over),
            )
        } else {
            // Orange (carried-in overflow) is laid down first, green (this
            // day's own consumption) follows right after it — left to right,
            // matching "carry placed before this day's own portion".
            Row(modifier = Modifier.fillMaxHeight()) {
                if (day.carryFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(day.carryFraction.coerceIn(0.04f, 1f))
                            .background(StatusColors.carry), // orange
                    )
                }
                if (day.ownFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(
                                // fillMaxWidth() here is relative to the Row's
                                // remaining width after the orange Box above,
                                // so this needs to be the own-fraction rescaled
                                // to that remaining space, not the segment total.
                                (day.ownFraction / (1f - day.carryFraction).coerceAtLeast(0.0001f))
                                    .coerceIn(0.04f, 1f),
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(StatusColors.ok), // green
                    )
                }
            }
        }
    }
}
