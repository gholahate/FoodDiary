package com.fooddiary.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fooddiary.android.ui.charts.ChartsScreen
import com.fooddiary.android.ui.common.rememberKeyboardVisible
import com.fooddiary.android.ui.history.DayDetailScreen
import com.fooddiary.android.ui.history.HistoryScreen
import com.fooddiary.android.ui.product.ProductEditorScreen
import com.fooddiary.android.ui.profile.ProfileScreen
import com.fooddiary.android.ui.scan.ScanScreen
import com.fooddiary.android.ui.settings.SettingsScreen
import com.fooddiary.android.ui.theme.AppTheme
import com.fooddiary.android.ui.today.TodayScreen
import com.fooddiary.android.ui.update.UpdateHost

private enum class AppTab(val label: String, val icon: ImageVector) {
    TODAY("Сегодня", Icons.Filled.Home),
    HISTORY("История", Icons.Filled.CalendarMonth),
    CHARTS("Графики", Icons.Filled.TrendingUp),
    PROFILE("Профиль", Icons.Filled.Person),
    SETTINGS("Настройки", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FoodDiaryApp).container
        setContent {
            val themeMode by container.settings.themeMode.collectAsState()
            AppTheme(mode = themeMode) {
                RootScaffold(container)
                // Daily update check on start + the "Доступна версия" window over any screen.
                UpdateHost(container.updates)
            }
        }
    }
}

@Composable
private fun RootScaffold(container: AppContainer) {
    var selectedTab by remember { mutableStateOf(AppTab.TODAY) }
    // editingProductId == null && isEditorOpen  -> creating a new product
    // editingProductId != null && isEditorOpen  -> editing that existing product
    var editingProductId by remember { mutableStateOf<String?>(null) }
    var isEditorOpen by remember { mutableStateOf(false) }
    // Bumped every time the editor is opened, so each session gets a fresh
    // ViewModel instance (see ProductEditorScreen's viewModel key) instead of
    // reusing one left over from a previous, already-saved session.
    var editorSessionId by remember { mutableStateOf(0) }
    // Non-null while viewing/editing a specific day opened from the History
    // calendar (see item 1: this used to be embedded inline under the
    // calendar, which left too little room for the add-product panels).
    var openDayDate by remember { mutableStateOf<String?>(null) }
    // True while the on-screen keyboard is open; the bottom menu is hidden then
    // (like the totals on the day screens) to leave more room for typing.
    val keyboardVisible by rememberKeyboardVisible()
    // Barcode scan screen (opened from the "+" menu on Today); a fresh
    // ViewModel per session, same as the editor.
    var isScanOpen by remember { mutableStateOf(false) }
    var scanSessionId by remember { mutableStateOf(0) }
    // Name of a product just added by scanning: Today filters its list by it.
    var pendingTodaySearch by remember { mutableStateOf<String?>(null) }

    // System back gesture / button. Without these the single Activity simply
    // closes, because nothing else intercepts "back". The editor sits on top
    // of everything (including a day screen), so it takes priority.
    BackHandler(enabled = isEditorOpen) {
        isEditorOpen = false
        editingProductId = null
    }
    BackHandler(enabled = isScanOpen && !isEditorOpen) {
        isScanOpen = false
    }
    BackHandler(enabled = openDayDate != null && !isEditorOpen && !isScanOpen) {
        openDayDate = null
    }

    // The editor must be checked before the day screen: it can be opened from
    // inside a day screen ("Изм." on a product), and then it has to cover it.
    if (isEditorOpen) {
        ProductEditorScreen(
            container = container,
            existingProductId = editingProductId,
            sessionKey = editorSessionId,
            onSaved = {
                isEditorOpen = false
                editingProductId = null
            },
            onCancel = {
                isEditorOpen = false
                editingProductId = null
            },
        )
        return
    }

    if (isScanOpen) {
        ScanScreen(
            container = container,
            sessionKey = scanSessionId,
            onClose = { isScanOpen = false },
            onAdded = { productName ->
                isScanOpen = false
                selectedTab = AppTab.TODAY
                pendingTodaySearch = productName
            },
            onCreateManually = {
                isScanOpen = false
                editingProductId = null
                editorSessionId++
                isEditorOpen = true
            },
        )
        return
    }

    if (openDayDate != null) {
        DayDetailScreen(
            container = container,
            date = openDayDate!!,
            onBack = { openDayDate = null },
            onChangeDate = { openDayDate = it },
            onEditProduct = { productId ->
                editingProductId = productId
                editorSessionId++
                isEditorOpen = true
            },
        )
        return
    }

    Scaffold(
        bottomBar = {
            if (!keyboardVisible) {
                // Material3's default bar height is 80dp; 64dp trims roughly 1mm
                // of empty space above and below the icons. Tweak this one number
                // to taste (below ~58-60dp the labels may start to get clipped).
                // Icons only, no captions (the caption is still read out by
                // TalkBack via contentDescription).
                NavigationBar(modifier = Modifier.height(64.dp)) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            AppTab.TODAY -> TodayScreen(
                container = container,
                modifier = Modifier.padding(padding),
                onEditProduct = { productId ->
                    editingProductId = productId
                    editorSessionId++
                    isEditorOpen = true
                },
                onAddProduct = {
                    editingProductId = null
                    editorSessionId++
                    isEditorOpen = true
                },
                onScanProduct = {
                    scanSessionId++
                    isScanOpen = true
                },
                searchRequest = pendingTodaySearch,
                onSearchRequestConsumed = { pendingTodaySearch = null },
            )
            AppTab.HISTORY -> HistoryScreen(
                container = container,
                modifier = Modifier.padding(padding),
                onOpenDay = { date -> openDayDate = date },
            )
            AppTab.CHARTS -> ChartsScreen(container = container, modifier = Modifier.padding(padding))
            AppTab.PROFILE -> ProfileScreen(container = container, modifier = Modifier.padding(padding))
            AppTab.SETTINGS -> SettingsScreen(settings = container.settings, backup = container.backup, updates = container.updates, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun PlaceholderScreen(text: String) {
    Text(text, modifier = Modifier.padding(16.dp))
}
