package com.malaxg.milo.feature.ledger

import android.Manifest
import android.graphics.BitmapFactory
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.ZoneId
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var isSaving by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val addTransaction = remember(repository) { AddTransactionUseCase(repository) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (uri != null) imageUri = uri.toString()
    }

    val recognizer = remember(context) { SpeechRecognizer.createSpeechRecognizer(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            saveStatus = "需要麦克风权限才能使用语音输入"
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            saveStatus = ""
            recognizer.startListening(speechIntent())
            isListening = true
        } else {
            saveStatus = "这台设备没有可用的语音识别服务，可以改用文字输入"
        }
    }
    DisposableEffect(recognizer) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                isListening = false
                if (text.isNullOrBlank()) {
                    saveStatus = "没有听清，再试一次或改用文字输入"
                    return
                }
                val parsed = parseCapture(text)
                amountText = parsed.amountYuan ?: amountText
                noteText = parsed.note
                selectedType = parsed.type
                voiceDraft = true
                saveStatus = "已整理语音，请检查后保存"
            }
            override fun onError(error: Int) {
                isListening = false
                saveStatus = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限才能使用语音输入"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "语音识别网络暂不可用，可以改用文字输入"
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "没有听清，再试一次或改用文字输入"
                    else -> "语音识别暂不可用，可以改用文字输入"
                }
            }
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
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            saveStatus = "这台设备没有可用的语音识别服务，可以改用文字输入"
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            saveStatus = ""
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
        isSaving = isSaving,
        saveStatus = saveStatus,
        imageUri = imageUri,
        onAmountChange = { amountText = it.filter { char -> char.isDigit() || char == '.' }; saveStatus = "" },
        onNoteChange = { noteText = it; saveStatus = "" },
        onTypeChange = { selectedType = it; saveStatus = "" },
        onVoiceClick = {
            if (isListening) {
                recognizer.stopListening()
                isListening = false
            } else {
                startVoice()
            }
        },
        onImageClick = { imagePicker.launch(arrayOf("image/*")) },
        onImageRemove = { imageUri = null },
        onQuickCapture = { amount, note ->
            amountText = amount
            noteText = note
            selectedType = TransactionType.EXPENSE
            voiceDraft = false
            saveStatus = ""
        },
        onSubmit = {
            if (!isSaving) {
                val parsed = if (amountText.isBlank()) parseCapture(noteText) else null
                val finalAmount = parsed?.amountYuan ?: amountText
                val finalNote = parsed?.note ?: noteText
                val cents = MoneyCents.fromYuan(finalAmount)
                if (cents.value > 0) {
                    isSaving = true
                    saveStatus = ""
                scope.launch {
                    try {
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
                        saveStatus = "记录已保存"
                    } catch (_: Exception) {
                        saveStatus = "保存失败，请检查后重试"
                    } finally {
                        isSaving = false
                    }
                }
                } else {
                    saveStatus = "请输入有效金额，或在备注中写明金额"
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
    isSaving: Boolean,
    saveStatus: String,
    imageUri: String?,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onVoiceClick: () -> Unit,
    onImageClick: () -> Unit,
    onImageRemove: () -> Unit,
    onQuickCapture: (String, String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneId = ZoneId.systemDefault()
    val monthTotals = summarizeMonth(transactions, YearMonth.now(zoneId), zoneId)
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
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
            }
            item { BalanceCard(expense = monthTotals.expenseCents, income = monthTotals.incomeCents) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("快速记一笔", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("手动记录", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            item {
                EntryCard(
                    amountText, noteText, selectedType, isListening, hasVoiceDraft, isSaving, saveStatus, imageUri,
                    onAmountChange, onNoteChange, onTypeChange, onVoiceClick, onImageClick, onImageRemove, onSubmit,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("常用场景", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("午饭" to "32", "地铁" to "5", "咖啡" to "18").forEach { (note, amount) ->
                            AssistChip(onClick = { onQuickCapture(amount, note) }, label = { Text(note) })
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("最近记录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(transactions.size.toString() + " 笔", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (transactions.isEmpty()) {
                item { EmptyState() }
            } else {
                items(transactions, key = { it.id }) { TransactionRow(it) }
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
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("本月结余", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodyMedium)
                    Text(MoneyCents(balance).toDisplayString(), color = Color.White,
                        style = MaterialTheme.typography.headlineLarge)
                }
                LedgerIllustration(Modifier.size(78.dp), tint = Color.White)
            }
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
    hasVoiceDraft: Boolean, isSaving: Boolean, saveStatus: String, imageUri: String?,
    onAmountChange: (String) -> Unit, onNoteChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit, onVoiceClick: () -> Unit, onImageClick: () -> Unit,
    onImageRemove: () -> Unit, onSubmit: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TransactionType.values().forEach { type ->
                    FilterChip(
                        selected = type == selectedType,
                        onClick = { onTypeChange(type) },
                        label = { Text(if (type == TransactionType.EXPENSE) "支出" else "收入") },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("¥", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = onAmountChange,
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineLarge,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = onNoteChange,
                    placeholder = { Text("记下这笔消费") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onImageClick) {
                    Text(if (imageUri == null) "+" else "✓", style = MaterialTheme.typography.titleLarge)
                }
            }
            if (imageUri != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val selectedPhoto = rememberLedgerImageBitmap(imageUri)
                    if (selectedPhoto != null) {
                        Image(selectedPhoto, contentDescription = "待保存的账目照片",
                            contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text("已添加一张照片", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton(onClick = onImageRemove) { Text("移除照片") }
                }
            }
            Button(
                onClick = onSubmit,
                enabled = !isSaving && canSaveAmount(amountText, noteText),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(if (isSaving) "正在保存…" else "保存记录") }
            if (saveStatus.isNotBlank()) {
                Text(saveStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun canSaveAmount(amountText: String, noteText: String): Boolean {
    val amount = amountText.ifBlank { parseCapture(noteText).amountYuan.orEmpty() }
    return MoneyCents.fromYuan(amount).value > 0
}

@Composable
private fun EmptyState() {
    Surface(
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            LedgerIllustration(Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("还没有记录", style = MaterialTheme.typography.titleMedium)
                Text("试试说一句“午饭 32 元”", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LedgerIllustration(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = (size.minDimension * .07f).coerceAtLeast(2.2f))
        drawRoundRect(
            tint,
            Offset(w * .12f, h * .27f),
            androidx.compose.ui.geometry.Size(w * .76f, h * .5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .1f),
            style = stroke,
        )
        drawLine(tint, Offset(w * .12f, h * .43f), Offset(w * .86f, h * .43f), strokeWidth = stroke.width)
        drawCircle(tint, radius = w * .055f, center = Offset(w * .68f, h * .57f))
        drawLine(tint, Offset(w * .25f, h * .2f), Offset(w * .34f, h * .1f), strokeWidth = stroke.width)
        drawLine(tint, Offset(w * .34f, h * .1f), Offset(w * .4f, h * .2f), strokeWidth = stroke.width)
    }
}

@Composable
private fun TransactionRow(transaction: Transaction, modifier: Modifier = Modifier) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val transactionPhotoUri = transaction.imageUri
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
            if (transactionPhotoUri != null) {
                val loadedBitmap = rememberLedgerImageBitmap(transactionPhotoUri)
                if (loadedBitmap != null) {
                    Image(
                        loadedBitmap,
                        contentDescription = "记录照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(42.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            Text((if (transaction.type == TransactionType.EXPENSE) "-" else "+") +
                MoneyCents(transaction.amountCents).toDisplayString(),
                color = if (transaction.type == TransactionType.EXPENSE) Coral else Sage,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun rememberLedgerImageBitmap(uriText: String): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uriText) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriText)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sampleSize = 1
                while (bounds.outWidth / sampleSize > 600 || bounds.outHeight / sampleSize > 600) {
                    sampleSize *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return bitmap
}

@Preview(showBackground = true)
@Composable
private fun LedgerScreenPreview() {
    MiloagentTheme {
        LedgerScreen(
            transactions = emptyList(), amountText = "32", noteText = "午饭",
            selectedType = TransactionType.EXPENSE, isListening = false, hasVoiceDraft = true,
            isSaving = false, saveStatus = "",
            onAmountChange = {}, onNoteChange = {}, onTypeChange = {}, onVoiceClick = {},
            onImageClick = {}, onImageRemove = {}, imageUri = null, onQuickCapture = { _, _ -> }, onSubmit = {},
        )
    }
}
