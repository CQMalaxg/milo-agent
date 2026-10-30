package com.malaxg.milo.feature.ledger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.malaxg.milo.core.common.MoneyCents
import com.malaxg.milo.core.common.toDisplayString
import com.malaxg.milo.core.designsystem.theme.Coral
import com.malaxg.milo.core.designsystem.theme.CoralSoft
import com.malaxg.milo.core.designsystem.theme.MiloagentTheme
import com.malaxg.milo.core.designsystem.theme.Sage
import com.malaxg.milo.core.designsystem.theme.SageSoft
import com.malaxg.milo.core.domain.model.CaptureSource
import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.core.domain.model.TransactionType
import com.malaxg.milo.core.domain.repository.LedgerRepository
import com.malaxg.milo.core.domain.usecase.AddTransactionUseCase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LedgerRoute(
    repository: LedgerRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val transactions by repository.observeAll().collectAsState(initial = emptyList())
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var isListening by remember { mutableStateOf(false) }
    var voiceDraft by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val addTransaction = remember(repository) { AddTransactionUseCase(repository) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri?.toString()
    }

    val recognizer = remember(context) { SpeechRecognizer.createSpeechRecognizer(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            recognizer.startListening(speechIntent())
            isListening = true
        }
    }
    DisposableEffect(recognizer) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?: return
                val parsed = parseCapture(text)
                amountText = parsed.amountYuan ?: amountText
                noteText = parsed.note
                selectedType = parsed.type
                voiceDraft = true
                isListening = false
            }
            override fun onError(error: Int) { isListening = false }
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        onDispose { recognizer.destroy() }
    }

    fun startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            recognizer.startListening(speechIntent())
            isListening = true
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LedgerScreen(
        transactions = transactions,
        amountText = amountText,
        noteText = noteText,
        selectedType = selectedType,
        isListening = isListening,
        hasVoiceDraft = voiceDraft,
        imageUri = imageUri,
        onAmountChange = { amountText = it.filter { char -> char.isDigit() || char == '.' } },
        onNoteChange = { noteText = it },
        onTypeChange = { selectedType = it },
        onVoiceClick = { if (isListening) recognizer.stopListening() else startVoice() },
        onImageClick = { imagePicker.launch("image/*") },
        onQuickCapture = { amount, note ->
            amountText = amount
            noteText = note
            voiceDraft = false
        },
        onSubmit = {
            val parsed = if (amountText.isBlank()) parseCapture(noteText) else null
            val finalAmount = parsed?.amountYuan ?: amountText
            val finalNote = parsed?.note ?: noteText
            val cents = MoneyCents.fromYuan(finalAmount)
            if (cents.value > 0) {
                scope.launch {
                    addTransaction(
                        amount = cents,
                        type = parsed?.type ?: selectedType,
                        note = finalNote,
                        source = if (voiceDraft) CaptureSource.VOICE else CaptureSource.MANUAL,
                        imageUri = imageUri,
                    )
                    amountText = ""
                    noteText = ""
                    voiceDraft = false
                    imageUri = null
                }
            }
        },
        modifier = modifier,
    )
}

private fun speechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
}

private data class CaptureParse(
    val amountYuan: String?,
    val note: String,
    val type: TransactionType,
)

private fun parseCapture(input: String): CaptureParse {
    val amount = Regex("""(?<!\d)(\d+(?:[.]\d{1,2})?)\s*(?:元|块|块钱)?""")
        .find(input)?.groupValues?.getOrNull(1)
    val type = if (input.contains("收入") || input.contains("工资") || input.contains("到账")) {
        TransactionType.INCOME
    } else {
        TransactionType.EXPENSE
    }
    return CaptureParse(amount, input.trim(), type)
}

@Composable
fun LedgerScreen(
    transactions: List<Transaction>,
    amountText: String,
    noteText: String,
    selectedType: TransactionType,
    isListening: Boolean,
    hasVoiceDraft: Boolean,
    imageUri: String?,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onVoiceClick: () -> Unit,
    onImageClick: () -> Unit,
    onQuickCapture: (String, String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
    val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .padding(innerPadding).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("早上好，今天也记得照顾自己", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("我的账本", style = MaterialTheme.typography.headlineLarge)
                }
                Surface(shape = CircleShape, color = SageSoft, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("M", color = Sage, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(18.dp))
            BalanceCard(expense = expense, income = income)
            Spacer(Modifier.height(18.dp))
            Text("快速记一笔", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            EntryCard(
                amountText, noteText, selectedType, isListening, hasVoiceDraft, imageUri,
                onAmountChange, onNoteChange, onTypeChange, onVoiceClick, onImageClick, onSubmit,
            )
            Spacer(Modifier.height(18.dp))
            Text("常用场景", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("午饭" to "32", "地铁" to "5", "咖啡" to "18").forEach { (note, amount) ->
                    AssistChip(onClick = { onQuickCapture(amount, note) }, label = { Text(note) })
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("最近记录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(transactions.size.toString() + " 笔", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            if (transactions.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
                ) {
                    items(transactions, key = { it.id }) { TransactionRow(it) }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(expense: Long, income: Long) {
    val balance = income - expense
    Card(
        colors = CardDefaults.cardColors(containerColor = Sage),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("本月结余", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodyMedium)
            Text(MoneyCents(balance).toDisplayString(), color = Color.White,
                style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BalanceMetric("支出", expense, CoralSoft)
                BalanceMetric("收入", income, SageSoft)
            }
        }
    }
}

@Composable
private fun BalanceMetric(label: String, amount: Long, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(tint, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label + "  " + MoneyCents(amount).toDisplayString(), color = Color.White,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EntryCard(
    amountText: String, noteText: String, selectedType: TransactionType, isListening: Boolean,
    hasVoiceDraft: Boolean, imageUri: String?, onAmountChange: (String) -> Unit, onNoteChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit, onVoiceClick: () -> Unit, onImageClick: () -> Unit, onSubmit: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("描述一下这笔花销", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AssistChip(onClick = onImageClick, label = { Text(if (imageUri == null) "添照片" else "已附图") })
                IconButton(onClick = onVoiceClick, modifier = Modifier.size(42.dp)) {
                    Surface(shape = CircleShape, color = if (isListening) CoralSoft else SageSoft) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (isListening) "■" else "●", color = if (isListening) Coral else Sage)
                        }
                    }
                }
            }
            if (isListening) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Coral)
                Text("正在听…说“午饭 32 元”即可", color = Coral, style = MaterialTheme.typography.bodySmall)
            }
            if (hasVoiceDraft) {
                Text("Milo 已整理，请确认后保存；空白字段可以稍后补充", color = Sage, style = MaterialTheme.typography.labelSmall)
            }
            OutlinedTextField(
                value = noteText, onValueChange = onNoteChange,
                label = { Text("例如：午饭 32 元") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TransactionType.values().forEach { type ->
                    FilterChip(
                        selected = type == selectedType,
                        onClick = { onTypeChange(type) },
                        label = { Text(if (type == TransactionType.EXPENSE) "支出" else "收入") },
                    )
                }
                OutlinedTextField(
                    value = amountText, onValueChange = onAmountChange,
                    label = { Text("金额") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = onSubmit,
                enabled = MoneyCents.fromYuan(amountText).value > 0 || noteText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (hasVoiceDraft) "确认并记账" else "记一笔") }
        }
    }
}

@Composable
private fun EmptyState() {
    Surface(
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有记录", style = MaterialTheme.typography.titleMedium)
            Text("试试说一句“午饭 32 元”", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransactionRow(transaction: Transaction, modifier: Modifier = Modifier) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Surface(
        modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = if (transaction.type == TransactionType.EXPENSE) CoralSoft else SageSoft,
                modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (transaction.type == TransactionType.EXPENSE) "支" else "收",
                        color = if (transaction.type == TransactionType.EXPENSE) Coral else Sage,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(transaction.note?.takeIf { it.isNotBlank() } ?: "未分类", style = MaterialTheme.typography.bodyLarge)
                Text(formatter.format(Date(transaction.occurredAt)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text((if (transaction.type == TransactionType.EXPENSE) "-" else "+") +
                MoneyCents(transaction.amountCents).toDisplayString(),
                color = if (transaction.type == TransactionType.EXPENSE) Coral else Sage,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LedgerScreenPreview() {
    MiloagentTheme {
        LedgerScreen(
            transactions = emptyList(), amountText = "32", noteText = "午饭",
            selectedType = TransactionType.EXPENSE, isListening = false, hasVoiceDraft = true,
            onAmountChange = {}, onNoteChange = {}, onTypeChange = {}, onVoiceClick = {},
            onImageClick = {}, imageUri = null, onQuickCapture = { _, _ -> }, onSubmit = {},
        )
    }
}
