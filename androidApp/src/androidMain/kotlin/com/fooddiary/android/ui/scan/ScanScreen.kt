package com.fooddiary.android.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fooddiary.android.AppContainer
import kotlin.math.roundToInt

/**
 * "Сканировать штрихкод": opens the camera scanner right away; behind it this
 * screen shows the search progress and then the results.
 *  - found → product card(s) with «Добавить» (saves into the product base);
 *  - not found → a message with «Отмена поиска» (and «Сканировать снова»,
 *    «Создать вручную»).
 * [onAdded] gets the saved product's name (Today then filters its list by it,
 * so the product can be added to the diary right away).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    container: AppContainer,
    sessionKey: Int,
    onClose: () -> Unit,
    onAdded: (productName: String) -> Unit,
    onCreateManually: () -> Unit,
) {
    val viewModel: ScanViewModel = viewModel(
        key = "scan-$sessionKey",
        factory = ScanViewModelFactory(container),
    )
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val current = state
    if (current is ScanUiState.Scanning) {
        LaunchedEffect(current.attempt) {
            startBarcodeScan(
                context = context,
                onScanned = viewModel::onScanned,
                // Closing the camera without scanning = the user changed their mind.
                onCancelled = onClose,
                onFailed = viewModel::onScanFailed,
            )
        }
    }
    if (current is ScanUiState.Added) {
        LaunchedEffect(current) { onAdded(current.productName) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Поиск по штрихкоду") },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (current) {
                is ScanUiState.Scanning -> Text("Наведите камеру на штрихкод упаковки…")

                is ScanUiState.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(
                        "Ищем код ${current.barcode} в Open Food Facts…",
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

                is ScanUiState.Found -> {
                    Text(
                        "Код ${current.barcode}. Найдено: ${current.items.size}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    current.items.forEachIndexed { index, item ->
                        FoundProductCard(
                            item = item,
                            saving = current.savingIndex == index,
                            enabled = current.savingIndex == null,
                            onAdd = { viewModel.addToBase(index) },
                        )
                    }
                    BottomActions(
                        primaryText = "Отмена поиска",
                        onPrimary = onClose,
                        onRescan = viewModel::rescan,
                    )
                }

                is ScanUiState.NotFound -> {
                    Text("Продукт не найден", fontWeight = FontWeight.Bold)
                    Text(
                        "Кода ${current.barcode} нет в базе Open Food Facts. Можно создать продукт вручную.",
                    )
                    TextButton(onClick = onCreateManually) { Text("Создать продукт вручную") }
                    BottomActions(primaryText = "Отмена поиска", onPrimary = onClose, onRescan = viewModel::rescan)
                }

                is ScanUiState.NotAProductCode -> {
                    Text("Это не штрихкод товара", fontWeight = FontWeight.Bold)
                    Text(
                        "В коде нет номера товара: «${current.rawValue.take(80)}». Отсканируйте штрихкод на упаковке.",
                    )
                    BottomActions(primaryText = "Отмена поиска", onPrimary = onClose, onRescan = viewModel::rescan)
                }

                is ScanUiState.Error -> {
                    Text("Что-то пошло не так", fontWeight = FontWeight.Bold)
                    Text(current.message)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::retry) { Text("Повторить") }
                        OutlinedButton(onClick = onClose) { Text("Отмена") }
                    }
                }

                is ScanUiState.Added -> Text("Добавлено в базу: ${current.productName}")
            }
        }
    }
}

@Composable
private fun FoundProductCard(
    item: ScanResultItem,
    saving: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit,
) {
    val p = item.product
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.displayName, fontWeight = FontWeight.Bold)
                    p.quantityLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                Button(onClick = onAdd, enabled = enabled, modifier = Modifier.padding(start = 8.dp)) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Добавить")
                    }
                }
            }

            val m = p.macrosPer100
            Text(
                "На 100 г: ${m.kcal.roundToInt()} ккал · Б ${m.protein.oneDecimal()} · Ж ${m.fat.oneDecimal()} · У ${m.carb.oneDecimal()}",
                style = MaterialTheme.typography.bodyMedium,
            )

            val measures = listOfNotNull(
                p.packageGrams?.let { "Упаковка = ${it.oneDecimal()} г" },
                p.servingGrams?.let { "Порция = ${it.oneDecimal()} г" },
            )
            if (measures.isNotEmpty()) {
                Text("Будут добавлены меры: ${measures.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }
            if (p.missingMacros.isNotEmpty()) {
                Text(
                    "Нет данных: ${p.missingMacros.joinToString(", ")} — будет 0, поправьте карандашом после добавления.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (item.alreadyInBase) {
                Text(
                    "В вашей базе уже есть продукт с таким названием.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun BottomActions(primaryText: String, onPrimary: () -> Unit, onRescan: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPrimary) { Text(primaryText) }
        TextButton(onClick = onRescan) { Text("Сканировать снова") }
    }
}

/** 12.0 -> "12", 3.456 -> "3.5". */
private fun Double.oneDecimal(): String {
    val r = (this * 10).roundToInt() / 10.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}
