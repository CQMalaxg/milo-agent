package com.malaxg.miloagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.malaxg.milo.core.designsystem.theme.MiloagentTheme
import com.malaxg.milo.core.domain.repository.LedgerRepository
import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.feature.ledger.LedgerRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as MiloApp).ledgerRepository
        val lifeStore = (application as MiloApp).lifeStore
        setContent { MiloagentTheme { MiloShell(repository, lifeStore) } }
    }
}

private enum class AppScreen { HOME, LEDGER, HISTORY, EDIT_LEDGER, JOURNAL, SCHEDULE, SETTINGS }

@Composable
private fun MiloShell(repository: LedgerRepository, lifeStore: LifeStore) {
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    val transactions by repository.observeAll().collectAsState(initial = emptyList())
    val isFocusedFlow = screen == AppScreen.EDIT_LEDGER
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BackHandler(enabled = screen != AppScreen.HOME) {
        if (screen == AppScreen.EDIT_LEDGER) editingTransaction = null
        screen = if (screen == AppScreen.EDIT_LEDGER) AppScreen.HISTORY else AppScreen.HOME
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        when (screen) {
            AppScreen.HOME -> HomeRoute(
                repository = repository,
                lifeStore = lifeStore,
                modifier = Modifier.weight(1f),
            )
            AppScreen.LEDGER -> LedgerRoute(repository, Modifier.weight(1f))
            AppScreen.HISTORY -> HistoryRoute(transactions, Modifier.weight(1f), onEdit = {
                editingTransaction = it
                screen = AppScreen.EDIT_LEDGER
            })
            AppScreen.EDIT_LEDGER -> editingTransaction?.let { transaction ->
                TransactionEditRoute(
                    transaction = transaction, repository = repository,
                    onClose = { editingTransaction = null; screen = AppScreen.HISTORY },
                    modifier = Modifier.weight(1f),
                )
            }
            AppScreen.JOURNAL -> JournalRoute(lifeStore, Modifier.weight(1f))
            AppScreen.SCHEDULE -> ScheduleRoute(lifeStore, Modifier.weight(1f))
            AppScreen.SETTINGS -> SettingsRoute({ screen = AppScreen.HOME }, Modifier.weight(1f))
        }
        if (!isFocusedFlow && !isKeyboardVisible) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomTab("首页", "home", screen == AppScreen.HOME) { screen = AppScreen.HOME }
                    BottomTab("账本", "wallet", screen == AppScreen.LEDGER) { screen = AppScreen.LEDGER }
                    BottomTab("历史", "history", screen == AppScreen.HISTORY) { screen = AppScreen.HISTORY }
                    BottomTab("随笔", "note", screen == AppScreen.JOURNAL) { screen = AppScreen.JOURNAL }
                    BottomTab("日程", "calendar", screen == AppScreen.SCHEDULE) { screen = AppScreen.SCHEDULE }
                    BottomTab("设置", "settings", screen == AppScreen.SETTINGS) { screen = AppScreen.SETTINGS }
                }
            }
        }
    }
}

@Composable
private fun BottomTab(label: String, icon: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick, modifier = Modifier.size(width = 58.dp, height = 56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MiloIcon(icon, selected = selected, modifier = Modifier.size(22.dp))
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
