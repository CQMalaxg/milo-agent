package com.malaxg.miloagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.malaxg.milo.core.designsystem.theme.Coral
import com.malaxg.milo.core.designsystem.theme.CoralSoft
import com.malaxg.milo.core.designsystem.theme.Gold
import com.malaxg.milo.core.designsystem.theme.MutedInk
import com.malaxg.milo.core.designsystem.theme.Sage
import com.malaxg.milo.core.designsystem.theme.SageSoft
import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.core.domain.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryRoute(transactions: List<Transaction>, modifier: Modifier = Modifier) {
    val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
    val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("账本回忆", style = MaterialTheme.typography.headlineLarge)
        Text("每一笔生活，都值得被好好看见。", color = MutedInk)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            InsightCard("支出", "¥" + formatCents(expense), CoralSoft, Coral, Modifier.weight(1f))
            InsightCard("收入", "¥" + formatCents(income), SageSoft, Sage, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("2026 年 10 月", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            AssistChip(onClick = {}, label = { Text("全部") })
        }
        Spacer(Modifier.height(10.dp))
        if (transactions.isEmpty()) {
            DoodleEmptyState(title = "你的生活时间线还很安静", caption = "从首页记下第一笔，Milo 会替你整理成一段段回忆。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(transactions, key = { it.id }) { transaction -> HistoryItem(transaction) }
            }
        }
    }
}

@Composable
private fun InsightCard(label: String, value: String, bg: Color, fg: Color, modifier: Modifier) {
    Surface(color = bg, shape = RoundedCornerShape(22.dp), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = fg, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(value, color = fg, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("本月", color = fg.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HistoryItem(transaction: Transaction) {
    val formatter = remember { SimpleDateFormat("MM 月 dd 日  HH:mm", Locale.getDefault()) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = if (transaction.type == TransactionType.EXPENSE) CoralSoft else SageSoft, shape = CircleShape, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(if (transaction.type == TransactionType.EXPENSE) "花" else "得", color = if (transaction.type == TransactionType.EXPENSE) Coral else Sage) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(transaction.note ?: "未命名记录", style = MaterialTheme.typography.titleMedium)
                Text(formatter.format(Date(transaction.occurredAt)), color = MutedInk, style = MaterialTheme.typography.bodySmall)
                if (transaction.imageUri != null) Text("▧ 有照片", color = Sage, style = MaterialTheme.typography.labelSmall)
            }
            Text((if (transaction.type == TransactionType.EXPENSE) "-" else "+") + formatCents(transaction.amountCents), color = if (transaction.type == TransactionType.EXPENSE) Coral else Sage, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun JournalRoute(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imageUri = it?.toString() }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("随笔小笺", style = MaterialTheme.typography.headlineLarge)
        Text("把今天的光、声音和碎碎念收进来。", color = MutedInk)
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("给今天起个小标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("写下此刻") }, minLines = 6, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { picker.launch("image/*") }, label = { Text(if (imageUri == null) "放一张照片" else "照片已放入") })
                    FilterChip(selected = saved, onClick = { saved = !saved }, label = { Text(if (saved) "已收藏" else "收藏这页") })
                }
                Button(onClick = { saved = true }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text(if (saved) "已收进随笔" else "保存这段心情") }
            }
        }
        Spacer(Modifier.height(18.dp))
        DoodleEmptyState(title = "留一点空白给自己", caption = "随笔会按日期排成一条轻盈的生活长廊。")
    }
}

@Composable
fun ScheduleRoute(modifier: Modifier = Modifier) {
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var voiceDraft by remember { mutableStateOf(false) }
    val events = remember { mutableStateListOf("10:30  给妈妈回电话", "18:00  去超市买水果") }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("日程胶囊", style = MaterialTheme.typography.headlineLarge)
                Text("让 Milo 帮你记住那些想做的事。", color = MutedInk)
            }
            Text("十月  ·  周四", color = Sage, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = .16f)), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("语音添加", style = MaterialTheme.typography.titleMedium)
                        Text("试试说：明天上午十点去看牙", color = MutedInk)
                    }
                    VoiceCaptureButton(onResult = { result ->
                        title = result.substringAfter(" ", result)
                        time = Regex("\\d{1,2}[:：]\\d{2}").find(result)?.value ?: "09:00"
                        voiceDraft = true
                    })
                }
                if (voiceDraft) Text("已整理成草稿，请确认下面的字段", color = Sage, style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("事项，可留空") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("时间，可留空") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth())
                Button(onClick = { if (title.isNotBlank()) { events.add(0, (time.ifBlank { "未定时" }) + "  " + title); title = ""; time = ""; voiceDraft = false } }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("确认加入日程") }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("今天和接下来", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(events) { event -> Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) { Text(event, modifier = Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyLarge) } }
        }
    }
}

@Composable
fun ChatRoute(modifier: Modifier = Modifier) {
    val messages = remember { mutableStateListOf("嗨，我是 Milo。你可以让我记账、写随笔或安排日程。") }
    var draft by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("和 Milo 聊聊", style = MaterialTheme.typography.headlineLarge)
        Text("一句自然的话，就是一个生活动作。", color = MutedInk)
        Spacer(Modifier.height(14.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            item { DoodleEmptyState(title = "今天想从哪里开始？", caption = "“午饭 32 元”“提醒我周六买花”“今天有点累”") }
            items(messages) { message -> ChatBubble(message) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            OutlinedTextField(value = draft, onValueChange = { draft = it }, placeholder = { Text("告诉 Milo 一件事…") }, modifier = Modifier.weight(1f), singleLine = true)
            IconButton(onClick = { if (draft.isNotBlank()) { messages.add(draft); messages.add("收到，我先整理成一张可确认的卡片。你确认后我再保存。"); draft = "" } }) { Surface(color = SageSoft, shape = CircleShape, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Text("↑", color = Sage, style = MaterialTheme.typography.titleLarge) } } }
        }
    }
}

@Composable
private fun ChatBubble(text: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(15.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun VoiceCaptureButton(onResult: (String) -> Unit) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    val recognizer = remember(context) { SpeechRecognizer.createSpeechRecognizer(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) { recognizer.startListening(voiceIntent()); listening = true } }
    DisposableEffect(recognizer) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) { results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onResult); listening = false }
            override fun onError(error: Int) { listening = false }
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
    val start = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { recognizer.startListening(voiceIntent()); listening = true } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    IconButton(onClick = { if (listening) recognizer.stopListening() else start() }) { Surface(color = if (listening) CoralSoft else SageSoft, shape = CircleShape, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Text(if (listening) "■" else "●", color = if (listening) Coral else Sage) } } }
}

private fun voiceIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN"); putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) }

@Composable
private fun DoodleEmptyState(title: String, caption: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(86.dp)) {
                val stroke = Stroke(width = 4f)
                drawCircle(SageSoft, radius = 34f, center = Offset(43f, 43f))
                val path = Path().apply { moveTo(25f, 50f); cubicTo(34f, 35f, 42f, 62f, 55f, 43f); cubicTo(61f, 35f, 68f, 40f, 72f, 31f) }
                drawPath(path, Sage, style = stroke)
                drawCircle(Coral, radius = 4f, center = Offset(27f, 29f))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(caption, color = MutedInk, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatCents(cents: Long): String = "%.2f".format(Locale.US, cents / 100.0)
