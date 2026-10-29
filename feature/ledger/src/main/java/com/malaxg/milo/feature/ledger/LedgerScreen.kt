package com.malaxg.milo.feature.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.malaxg.milo.core.common.MoneyCents
import com.malaxg.milo.core.common.toDisplayString
import com.malaxg.milo.core.designsystem.theme.MiloagentTheme
import com.malaxg.milo.core.domain.model.CaptureSource
import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.core.domain.model.TransactionType
import com.malaxg.milo.core.domain.repository.LedgerRepository
import com.malaxg.milo.core.domain.usecase.AddTransactionUseCase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记账页路由：持有状态、连接仓库。
 *
 * 刻意与 [LedgerScreen] 分离——Screen 无状态，才能直接 Preview。
 */
@Composable
fun LedgerRoute(
    repository: LedgerRepository,
    modifier: Modifier = Modifier,
) {
    val transactions by repository.observeAll().collectAsState(initial = emptyList())
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }

    val scope = rememberCoroutineScope()
    val addTransaction = remember(repository) { AddTransactionUseCase(repository) }

    LedgerScreen(
        transactions = transactions,
        amountText = amountText,
        noteText = noteText,
        selectedType = selectedType,
        onAmountChange = { input -> amountText = input.filter { it.isDigit() || it == '.' } },
        onNoteChange = { noteText = it },
        onTypeChange = { selectedType = it },
        onSubmit = {
            val cents = MoneyCents.fromYuan(amountText)
            if (cents.value > 0) {
                scope.launch {
                    addTransaction(
                        amount = cents,
                        type = selectedType,
                        note = noteText,
                    )
                    amountText = ""
                    noteText = ""
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
fun LedgerScreen(
    transactions: List<Transaction>,
    amountText: String,
    noteText: String,
    selectedType: TransactionType,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "记账",
                style = MaterialTheme.typography.headlineMedium,
            )

            EntryCard(
                amountText = amountText,
                noteText = noteText,
                selectedType = selectedType,
                onAmountChange = onAmountChange,
                onNoteChange = onNoteChange,
                onTypeChange = onTypeChange,
                onSubmit = onSubmit,
            )

            SummaryRow(transactions = transactions)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = transactions, key = { it.id }) { transaction ->
                    TransactionRow(transaction = transaction)
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    amountText: String,
    noteText: String,
    selectedType: TransactionType,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.values().forEach { type ->
                    TextButton(onClick = { onTypeChange(type) }) {
                        Text(
                            text = if (type == TransactionType.EXPENSE) "支出" else "收入",
                            color = if (type == selectedType) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = onAmountChange,
                label = { Text("金额（元）") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = noteText,
                onValueChange = onNoteChange,
                label = { Text("备注") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onSubmit,
                enabled = MoneyCents.fromYuan(amountText).value > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("记一笔")
            }
        }
    }
}

@Composable
private fun SummaryRow(
    transactions: List<Transaction>,
    modifier: Modifier = Modifier,
) {
    val expense = transactions
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amountCents }
    val income = transactions
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amountCents }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "支出 ${MoneyCents(expense).toDisplayString()}",
            color = Color(0xFFC62828),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "收入 ${MoneyCents(income).toDisplayString()}",
            color = Color(0xFF2E7D32),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    modifier: Modifier = Modifier,
) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.note?.takeIf { it.isNotBlank() } ?: "未分类",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = formatter.format(Date(transaction.occurredAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = (if (transaction.type == TransactionType.EXPENSE) "-" else "+") +
                    MoneyCents(transaction.amountCents).toDisplayString(),
                color = if (transaction.type == TransactionType.EXPENSE) {
                    Color(0xFFC62828)
                } else {
                    Color(0xFF2E7D32)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Preview(showBackground = true, name = "记账页 - 亮色")
@Composable
private fun LedgerScreenPreview() {
    MiloagentTheme(dynamicColor = false) {
        LedgerScreen(
            transactions = previewTransactions,
            amountText = "32",
            noteText = "午饭",
            selectedType = TransactionType.EXPENSE,
            onAmountChange = {},
            onNoteChange = {},
            onTypeChange = {},
            onSubmit = {},
        )
    }
}

@Preview(showBackground = true, name = "记账页 - 空列表", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LedgerScreenEmptyPreview() {
    MiloagentTheme(dynamicColor = false) {
        LedgerScreen(
            transactions = emptyList(),
            amountText = "",
            noteText = "",
            selectedType = TransactionType.EXPENSE,
            onAmountChange = {},
            onNoteChange = {},
            onTypeChange = {},
            onSubmit = {},
        )
    }
}

private val previewTransactions: List<Transaction>
    get() {
        val now = System.currentTimeMillis()
        return listOf(
            Transaction(
                id = "1",
                amountCents = 3200,
                type = TransactionType.EXPENSE,
                note = "午饭",
                occurredAt = now,
                createdAt = now,
                source = CaptureSource.MANUAL,
            ),
            Transaction(
                id = "2",
                amountCents = 599,
                type = TransactionType.EXPENSE,
                note = "地铁",
                occurredAt = now - 3_600_000,
                createdAt = now - 3_600_000,
                source = CaptureSource.MANUAL,
            ),
            Transaction(
                id = "3",
                amountCents = 1_200_000,
                type = TransactionType.INCOME,
                note = "工资",
                occurredAt = now - 86_400_000,
                createdAt = now - 86_400_000,
                source = CaptureSource.MANUAL,
            ),
        )
    }
