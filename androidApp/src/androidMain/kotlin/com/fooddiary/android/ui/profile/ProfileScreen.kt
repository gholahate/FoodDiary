package com.fooddiary.android.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fooddiary.android.AppContainer

@Composable
fun ProfileScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(container))
    val state by viewModel.state.collectAsState()

    if (!state.isLoaded) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Профиль", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChanged,
            label = { Text("Имя") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            singleLine = true,
        )

        Text(
            "Включите параметры, которые хотите отслеживать. Можно выбрать несколько — каждый включённый параметр появится в виде прогресс-бара на главном экране. Цель задаётся на один день; недельная шкала строится из семи таких дней.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )

        Divider()
        GoalRow(
            label = "Калории",
            unit = "ккал",
            tracked = state.trackKcal,
            onTrackedChange = viewModel::onTrackKcalChanged,
            valueText = state.kcalText,
            onValueChange = viewModel::onKcalTextChanged,
        )
        Divider()
        GoalRow(
            label = "Белки",
            unit = "г",
            tracked = state.trackProtein,
            onTrackedChange = viewModel::onTrackProteinChanged,
            valueText = state.proteinText,
            onValueChange = viewModel::onProteinTextChanged,
        )
        Divider()
        GoalRow(
            label = "Жиры",
            unit = "г",
            tracked = state.trackFat,
            onTrackedChange = viewModel::onTrackFatChanged,
            valueText = state.fatText,
            onValueChange = viewModel::onFatTextChanged,
        )
        Divider()
        GoalRow(
            label = "Углеводы",
            unit = "г",
            tracked = state.trackCarb,
            onTrackedChange = viewModel::onTrackCarbChanged,
            valueText = state.carbText,
            onValueChange = viewModel::onCarbTextChanged,
        )
        Divider()

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        Text(
            if (state.savedJustNow) "Сохранено ✓ Изменения применяются сразу." else "Изменения сохраняются автоматически.",
            style = MaterialTheme.typography.bodySmall,
            color = if (state.savedJustNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun GoalRow(
    label: String,
    unit: String,
    tracked: Boolean,
    onTrackedChange: (Boolean) -> Unit,
    valueText: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            if (tracked) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = onValueChange,
                    label = { Text("Цель на день, $unit") },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
        Switch(checked = tracked, onCheckedChange = onTrackedChange, modifier = Modifier.padding(start = 8.dp))
    }
}
