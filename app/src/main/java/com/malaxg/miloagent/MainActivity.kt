package com.malaxg.miloagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.malaxg.milo.core.designsystem.theme.MiloagentTheme
import com.malaxg.milo.core.domain.repository.LedgerRepository
import com.malaxg.milo.feature.ledger.LedgerRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as MiloApp).ledgerRepository
        setContent { MiloagentTheme { MiloShell(repository) } }
    }
}

private enum class AppScreen { LEDGER, HISTORY, JOURNAL, SCHEDULE, CHAT, SETTINGS }

@Composable
private fun MiloShell(repository: LedgerRepository) {
    var screen by remember { mutableStateOf(AppScreen.LEDGER) }
    val transactions by repository.observeAll().collectAsState(initial = emptyList())
    BackHandler(enabled = screen != AppScreen.LEDGER) { screen = AppScreen.LEDGER }

    Column(Modifier.fillMaxSize()) {
        when (screen) {
            AppScreen.LEDGER -> LedgerRoute(repository, Modifier.weight(1f))
            AppScreen.HISTORY -> HistoryRoute(transactions, Modifier.weight(1f))
            AppScreen.JOURNAL -> JournalRoute(Modifier.weight(1f))
            AppScreen.SCHEDULE -> ScheduleRoute(Modifier.weight(1f))
            AppScreen.CHAT -> ChatRoute(Modifier.weight(1f))
            AppScreen.SETTINGS -> SettingsRoute({ screen = AppScreen.LEDGER }, Modifier.weight(1f))
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 5.dp,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth()) {
                BottomTab("账本", screen == AppScreen.LEDGER, { screen = AppScreen.LEDGER }, Modifier.weight(1f))
                BottomTab("历史", screen == AppScreen.HISTORY, { screen = AppScreen.HISTORY }, Modifier.weight(1f))
                BottomTab("随笔", screen == AppScreen.JOURNAL, { screen = AppScreen.JOURNAL }, Modifier.weight(1f))
                BottomTab("日程", screen == AppScreen.SCHEDULE, { screen = AppScreen.SCHEDULE }, Modifier.weight(1f))
                BottomTab("Milo", screen == AppScreen.CHAT, { screen = AppScreen.CHAT }, Modifier.weight(1f))
                BottomTab("设置", screen == AppScreen.SETTINGS, { screen = AppScreen.SETTINGS }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BottomTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    TextButton(onClick = onClick, modifier = modifier.padding(vertical = 3.dp)) {
        Text(
            text = if (selected) "● $label" else "○ $label",
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
