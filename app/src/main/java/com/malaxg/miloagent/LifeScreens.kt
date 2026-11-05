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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.core.content.ContextCompat
import com.malaxg.milo.core.common.MoneyCents
import com.malaxg.milo.core.common.toDisplayString
import com.malaxg.milo.core.designsystem.theme.Coral
import com.malaxg.milo.core.designsystem.theme.CoralSoft
import com.malaxg.milo.core.designsystem.theme.Gold
import com.malaxg.milo.core.designsystem.theme.Ink
import com.malaxg.milo.core.designsystem.theme.MutedInk
import com.malaxg.milo.core.designsystem.theme.Sage
import com.malaxg.milo.core.designsystem.theme.SageSoft
import com.malaxg.milo.core.domain.model.CaptureSource
import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.core.domain.model.TransactionType
import com.malaxg.milo.core.domain.repository.LedgerRepository
import com.malaxg.milo.core.domain.usecase.AddTransactionUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.YearMonth
import java.time.ZoneId
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private data class ChatMessage(val text: String, val fromUser: Boolean)

@Composable
fun HomeRoute(
    repository: LedgerRepository,
    lifeStore: LifeStore,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val aiClient = remember(context) { MiloAiClient(context) }
    val addTransaction = remember(repository) { AddTransactionUseCase(repository) }
    val scope = rememberCoroutineScope()
    val recognizer = remember(context) { SpeechRecognizer.createSpeechRecognizer(context) }
    val savedConversation = remember(lifeStore) { lifeStore.loadHomeConversation() }
    val messages = remember { mutableStateListOf<ChatMessage>().apply {
        addAll(savedConversation.messages.map { ChatMessage(it.text, it.fromUser) })
    } }
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var inputSource by remember { mutableStateOf(CaptureSource.TEXT_AI) }
    var pendingDraft by remember { mutableStateOf(savedConversation.pendingDraft) }
    var clarificationInput by remember { mutableStateOf<String?>(null) }
    var clarificationSource by remember { mutableStateOf<CaptureSource?>(null) }
    var listening by remember { mutableStateOf(false) }
    var voiceMode by remember { mutableStateOf(false) }
    var voicePressed by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var requestSequence by remember { mutableStateOf(0) }

    fun sendMessage() {
        val message = inputText.trim()
        if (message.isBlank() || isProcessing || pendingDraft != null) return
        val source = clarificationSource ?: inputSource
        val localDraft = if (clarificationInput == null) parseLocalCapture(message, source) else null
        val isLocalCapture = localDraft != null && localDraft.intent != CaptureIntent.CHAT
        if (!isLocalCapture && !aiClient.isConfigured()) {
            status = "请先到设置中配置 AI，再发送消息"
            return
        }
        val requestText = clarificationInput?.let { buildClarificationRequest(it, message) } ?: message
        val history = messages.map { turn ->
            MiloChatTurn(
                role = if (turn.fromUser) MiloChatRole.USER else MiloChatRole.ASSISTANT,
                content = turn.text,
            )
        }
        messages += ChatMessage(message, fromUser = true)
        inputText = ""
        inputSource = CaptureSource.TEXT_AI
        status = ""
        val requestId = ++requestSequence
        isProcessing = true
        scope.launch {
            if (isLocalCapture) {
                pendingDraft = localDraft!!.copy(source = source)
                clarificationInput = null
                clarificationSource = null
                isProcessing = false
                return@launch
            }
            when (val result = aiClient.extract(requestText)) {
                is MiloAiResult.Capture -> if (requestId == requestSequence) {
                    val resolvedDraft = result.draft
                    if (resolvedDraft.intent == CaptureIntent.CHAT) {
                        clarificationInput = null
                        clarificationSource = null
                        when (val reply = aiClient.chat(message, history)) {
                            is MiloAiResult.Reply -> messages += ChatMessage(reply.text, fromUser = false)
                            is MiloAiResult.Failure -> messages += ChatMessage(reply.reason, fromUser = false)
                            is MiloAiResult.Clarification -> messages += ChatMessage(reply.question, fromUser = false)
                            is MiloAiResult.Capture -> messages += ChatMessage("我暂时无法回复这条消息，请再试一次。", fromUser = false)
                        }
                    } else {
                        pendingDraft = resolvedDraft.copy(source = source)
                        clarificationInput = null
                        clarificationSource = null
                    }
                }
                is MiloAiResult.Clarification -> if (requestId == requestSequence) {
                    clarificationInput = requestText
                    clarificationSource = source
                    messages += ChatMessage(result.question, fromUser = false)
                }
                is MiloAiResult.Failure -> if (requestId == requestSequence) {
                    messages += ChatMessage(result.reason, fromUser = false)
                }
                is MiloAiResult.Reply -> if (requestId == requestSequence) {
                    messages += ChatMessage(result.text, fromUser = false)
                }
            }
            if (requestId == requestSequence) isProcessing = false
        }
    }

    fun confirmAction() {
        val draft = pendingDraft ?: return
        when (draft.intent) {
            CaptureIntent.EXPENSE, CaptureIntent.INCOME -> {
                val amount = MoneyCents.fromYuan(draft.amount)
                val occurredAt = draft.occurredAtMillis()
                if (amount.value <= 0) {
                    status = "请补充有效金额后确认"
                    return
                }
                if (occurredAt == null) {
                    status = "日期或时间格式不正确，请检查后再确认"
                    return
                }
                isSaving = true
                scope.launch {
                    val saved = runCatching {
                        addTransaction(
                            amount,
                            if (draft.intent == CaptureIntent.INCOME) TransactionType.INCOME else TransactionType.EXPENSE,
                            categoryId = draft.category,
                            note = draft.title,
                            occurredAt = occurredAt,
                            source = draft.source,
                        )
                    }.isSuccess
                    if (saved) {
                        pendingDraft = null
                        messages += ChatMessage(if (draft.intent == CaptureIntent.INCOME) "收入已保存到账本。" else "记录已保存到账本。", fromUser = false)
                    } else status = "保存失败，请稍后重试"
                    isSaving = false
                }
            }
            CaptureIntent.JOURNAL -> {
                val saved = lifeStore.addJournal(draft.title, draft.content.ifBlank { draft.rawText }, emptyList())
                if (saved != null) {
                    pendingDraft = null
                    messages += ChatMessage("随笔已保存。", fromUser = false)
                } else status = "请补充随笔内容后确认"
            }
            CaptureIntent.SCHEDULE -> {
                val saved = lifeStore.addSchedule(draft.title, draft.date, draft.time)
                if (saved != null) {
                    pendingDraft = null
                    messages += ChatMessage("日程已保存。", fromUser = false)
                } else status = "请检查事项、日期和时间后再确认"
            }
            CaptureIntent.CHAT -> Unit
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            status = "需要麦克风权限才能使用语音输入"
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "权限已开启，请按住下方录音"
        } else status = "这台设备没有可用的语音识别服务，可以改用文字输入"
    }
    DisposableEffect(recognizer) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val recognized = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (recognized.isNotBlank()) {
                    inputText = listOf(inputText.trim(), recognized.trim()).filter(String::isNotBlank).joinToString(" ")
                    inputSource = CaptureSource.VOICE
                    status = "语音已转成文字，可以检查后发送"
                }
                listening = false
            }
            override fun onError(error: Int) {
                listening = false
                status = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "系统语音识别服务没有麦克风权限，请在系统设置中为当前识别应用开启麦克风权限"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别网络暂不可用，可以改用文字输入"
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听清，再试一次或改用文字输入"
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
        onDispose {
            recognizer.cancel()
            recognizer.destroy()
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "这台设备没有可用的语音识别服务，可以改用文字输入"
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voicePressed = true
            recognizer.startListening(captureIntent())
            listening = true
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun stopListening() {
        voicePressed = false
        if (listening) recognizer.stopListening()
    }

    SideEffect {
        lifeStore.saveHomeConversation(
            messages.map { HomeConversationMessage(it.text, it.fromUser) },
            pendingDraft,
        )
    }

    val displayedItemCount = (messages.size.coerceAtLeast(1)) +
        (if (pendingDraft == null) 0 else 1) + (if (status.isBlank()) 0 else 1)
    LaunchedEffect(displayedItemCount) {
        if (displayedItemCount > 0) listState.animateScrollToItem(displayedItemCount - 1)
    }

    Column(
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .imePadding(),
    ) {
        if (!isKeyboardVisible) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(remember { SimpleDateFormat("EEEE · M 月 d 日", Locale.SIMPLIFIED_CHINESE).format(Date()) }, color = MutedInk, style = MaterialTheme.typography.labelSmall)
                    Text("Milo", style = MaterialTheme.typography.headlineLarge)
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 14.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Ink), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(com.malaxg.miloagent.R.drawable.milo_agent_logo),
                                contentDescription = "Milo",
                                modifier = Modifier.size(66.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("你好，我是 Milo。", style = MaterialTheme.typography.titleMedium)
                                Text("告诉我你想聊什么，或交给我整理一件事。", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            items(messages) { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        color = if (message.fromUser) SageSoft else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Text(message.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            pendingDraft?.let { draft ->
                item(key = "pending-action") {
                    HomeActionCard(
                        draft = draft,
                        onDraftChange = { pendingDraft = it },
                        onConfirm = { confirmAction() },
                        onCancel = { pendingDraft = null; status = "已取消这项操作" },
                        isSaving = isSaving,
                    )
                }
            }
            if (status.isNotBlank()) item(key = "home-status") {
                Text(status, color = MutedInk, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
            }
            if (isProcessing) item(key = "home-processing") {
                Text("Milo 正在思考…", color = MutedInk, style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Ink.copy(alpha = .7f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                if (!voiceMode) OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it; inputSource = CaptureSource.TEXT_AI; status = "" },
                    placeholder = { Text("发消息给 Milo…") },
                    enabled = !isProcessing && pendingDraft == null && !listening,
                    minLines = 1,
                    maxLines = 4,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { sendMessage() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { voiceMode = !voiceMode; status = if (voiceMode) "按住录音，说完松开" else "" },
                    enabled = !isProcessing && pendingDraft == null,
                    modifier = Modifier.semantics { contentDescription = if (voiceMode) "切换文字输入" else "语音输入" },
                ) { MiloIcon(if (voiceMode) "note" else "mic", Modifier.size(22.dp), selected = true, tint = Sage) }
                if (!voiceMode) IconButton(
                    onClick = { sendMessage() },
                    enabled = inputText.isNotBlank() && !isProcessing && pendingDraft == null,
                    modifier = Modifier.semantics { contentDescription = "发送" },
                ) { MiloIcon("send", Modifier.size(22.dp), selected = true, tint = Sage) }
                if (voiceMode) IconButton(
                    onClick = { sendMessage() },
                    enabled = inputText.isNotBlank() && !isProcessing && pendingDraft == null,
                    modifier = Modifier.semantics { contentDescription = "发送识别内容" },
                ) { MiloIcon("send", Modifier.size(22.dp), selected = true, tint = Sage) }
                if (voiceMode) {
                    Surface(
                        color = if (listening) CoralSoft else SageSoft,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(56.dp).pointerInput(voiceMode) {
                            detectTapGestures(onPress = {
                                startListening()
                                tryAwaitRelease()
                                stopListening()
                            })
                        }.semantics { contentDescription = "按住说话" },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (listening) "正在聆听，松开发送" else "按住说话", color = if (listening) Coral else Sage)
                        }
                    }
                }
            }
        }
        if (status.isNotBlank()) Spacer(Modifier.height(4.dp))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun HomeActionCard(
    draft: MiloDraft,
    onDraftChange: (MiloDraft) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    isSaving: Boolean,
) {
    val canConfirm = when (draft.intent) {
        CaptureIntent.EXPENSE, CaptureIntent.INCOME -> MoneyCents.fromYuan(draft.amount).value > 0 && draft.occurredAtMillis() != null
        CaptureIntent.JOURNAL -> draft.content.ifBlank { draft.rawText }.isNotBlank()
        CaptureIntent.SCHEDULE -> draft.title.isNotBlank() && isValidScheduleTiming(draft.date, draft.time)
        CaptureIntent.CHAT -> false
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = SageSoft),
        border = BorderStroke(1.dp, Sage.copy(alpha = .6f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiloIcon("spark", Modifier.size(20.dp), selected = true, tint = Sage)
                Text("待确认 · ${draft.intent.label()}", style = MaterialTheme.typography.titleMedium)
            }
            if (draft.rawText.isNotBlank()) Text("原话：${draft.rawText}", color = MutedInk, style = MaterialTheme.typography.bodySmall)
            when (draft.intent) {
                CaptureIntent.EXPENSE, CaptureIntent.INCOME -> {
                    OutlinedTextField(
                        value = draft.amount,
                        onValueChange = { onDraftChange(draft.copy(amount = it.filter { c -> c.isDigit() || c == '.' })) },
                        label = { Text("金额（元）") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { onDraftChange(draft.copy(title = it)) },
                        label = { Text(if (draft.intent == CaptureIntent.INCOME) "收入来源或备注" else "用途或备注") },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.category,
                        onValueChange = { onDraftChange(draft.copy(category = it)) },
                        label = { Text("分类，可留空") },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CaptureIntent.JOURNAL -> {
                    OutlinedTextField(draft.title, { onDraftChange(draft.copy(title = it)) }, label = { Text("标题，可留空") }, enabled = !isSaving, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.content.ifBlank { draft.rawText }, { onDraftChange(draft.copy(content = it)) }, label = { Text("随笔内容") }, minLines = 3, enabled = !isSaving, modifier = Modifier.fillMaxWidth())
                }
                CaptureIntent.SCHEDULE -> {
                    OutlinedTextField(draft.title, { onDraftChange(draft.copy(title = it)) }, label = { Text("要做什么？") }, enabled = !isSaving, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(draft.date, { onDraftChange(draft.copy(date = it)) }, label = { Text("日期，可留空") }, enabled = !isSaving, modifier = Modifier.weight(1f))
                        OutlinedTextField(draft.time, { onDraftChange(draft.copy(time = it)) }, label = { Text("时间，可留空") }, enabled = !isSaving, modifier = Modifier.weight(1f))
                    }
                }
                CaptureIntent.CHAT -> Unit
            }
            if (!canConfirm) Text("请补齐必填内容，并检查日期和时间格式。", color = MutedInk, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel, enabled = !isSaving) { Text("取消") }
                Button(onClick = onConfirm, enabled = canConfirm && !isSaving) { Text(if (isSaving) "保存中…" else "确认保存") }
            }
        }
    }
}

@Composable
private fun HomeAction(label: String, icon: String, bg: Color, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = bg, border = BorderStroke(1.dp, tint.copy(alpha = .38f)), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            MiloIcon(icon, Modifier.size(26.dp), selected = true, tint = tint)
            Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun MiloCaptureRoute(
    repository: LedgerRepository,
    lifeStore: LifeStore,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(MiloDraft(source = CaptureSource.TEXT_AI)) }
    var listening by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var isRefining by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var clarificationQuestion by remember { mutableStateOf<String?>(null) }
    var clarificationReply by remember { mutableStateOf("") }
    var requestSequence by remember { mutableStateOf(0) }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    val addTransaction = remember(repository) { AddTransactionUseCase(repository) }
    val aiClient = remember(context) { MiloAiClient(context) }
    val recognizer = remember(context) { SpeechRecognizer.createSpeechRecognizer(context) }

    fun parseInput(text: String, source: CaptureSource) {
        if (text.isBlank()) {
            status = "先说一句或输入内容"
            return
        }
        val question = clarificationQuestion
        if (question != null && clarificationReply.isBlank()) {
            status = "请先回答 Milo 的问题，再继续整理"
            return
        }
        val requestText = if (question == null) text else buildClarificationRequest(text, clarificationReply)
        draft = parseLocalCapture(requestText, source).copy(rawText = text)
        if (!aiClient.isConfigured()) {
            status = "已用本地规则整理，请检查并确认草稿"
            clarificationQuestion = null
            clarificationReply = ""
            return
        }

        val requestId = ++requestSequence
        isRefining = true
        status = if (question == null) "Milo 正在整理这句话…" else "Milo 正在整理补充信息…"
        scope.launch {
            when (val result = aiClient.extract(requestText)) {
                is MiloAiResult.Capture -> {
                    if (requestId == requestSequence) {
                        draft = result.draft.copy(source = source)
                        clarificationQuestion = null
                        clarificationReply = ""
                        status = "整理完成，字段都可以修改"
                    }
                }
                is MiloAiResult.Clarification -> {
                    if (requestId == requestSequence) {
                        clarificationQuestion = result.question
                        clarificationReply = ""
                        status = "还需要一点信息，回答后可以继续整理"
                    }
                }
                is MiloAiResult.Failure -> {
                    if (requestId == requestSequence) {
                        status = "模型暂不可用，已保留本地草稿：${result.reason}"
                    }
                }
                is MiloAiResult.Reply -> Unit
            }
            if (requestId == requestSequence) isRefining = false
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            status = "需要麦克风权限才能使用语音输入"
        } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer.startListening(captureIntent())
            listening = true
        } else {
            status = "这台设备没有可用的语音识别服务，可以改用文字输入"
        }
    }
    DisposableEffect(recognizer) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text ->
                    parseInput(text, CaptureSource.VOICE)
                }
                listening = false
            }
            override fun onError(error: Int) {
                listening = false
                status = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "系统语音识别服务没有麦克风权限，请在系统设置中为当前识别应用开启麦克风权限"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别网络暂不可用，可以改用文字输入"
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听清，再试一次或改用文字输入"
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
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status = "这台设备没有可用的语音识别服务，可以改用文字输入"
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recognizer.startListening(captureIntent()); listening = true
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun stopListening() {
        recognizer.stopListening()
        listening = false
        if (status.isBlank()) status = "已停止，请检查草稿内容"
    }

    Column(
        modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .imePadding(),
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) { MiloIcon("back", Modifier.size(24.dp), selected = true) }
            Column(Modifier.weight(1f)) {
                Text("Milo 工作台", style = MaterialTheme.typography.titleLarge)
                Text("先说出来，再由你确认", color = MutedInk, style = MaterialTheme.typography.bodySmall)
            }
            MiloIcon("spark", Modifier.size(30.dp), selected = true, tint = Coral)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 30.dp),
        ) {
            item {
                Surface(color = if (listening) CoralSoft else SageSoft, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(onClick = { if (listening) stopListening() else startListening() }, color = if (listening) Coral else Sage, shape = CircleShape, modifier = Modifier.size(92.dp)) {
                                Box(contentAlignment = Alignment.Center) { MiloIcon(if (listening) "stop" else "mic", Modifier.size(38.dp), selected = true, tint = Color.White) }
                            }
                            Text(if (listening) "正在听，讲完点击停止" else "点击麦克风开始说", style = MaterialTheme.typography.titleMedium)
                            Text("例如：午饭 32 元，或者提醒我周六买花", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                            if (listening) LinearProgressIndicator(color = Coral, modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.width(10.dp))
                        MiloIllustration("companion", Modifier.size(90.dp), tint = Ink)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = draft.rawText,
                    onValueChange = { draft = draft.copy(rawText = it, source = CaptureSource.TEXT_AI) },
                    label = { Text("也可以直接输入一句话") },
                    minLines = 1,
                    maxLines = 3,
                    enabled = !isRefining,
                    modifier = Modifier.fillMaxWidth(),
                )
                clarificationQuestion?.let { question ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("还需要确认", style = MaterialTheme.typography.titleSmall)
                        Text(question, color = MutedInk, style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            value = clarificationReply,
                            onValueChange = { clarificationReply = it },
                            label = { Text("补充信息") },
                            enabled = !isRefining,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Button(
                    onClick = { parseInput(draft.rawText, draft.source) },
                    enabled = draft.rawText.isNotBlank() && (clarificationQuestion == null || clarificationReply.isNotBlank()) && !isRefining,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            isRefining -> "正在整理…"
                            clarificationQuestion != null -> "补充信息并继续整理"
                            else -> "整理成待确认草稿"
                        },
                    )
                }
            }
            item {
                Text("我想让 Milo 做什么？", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CaptureIntent.entries.forEach { intent ->
                        FilterChip(
                            selected = draft.intent == intent,
                            onClick = { draft = draft.switchIntent(intent) },
                            label = { Text(intent.label()) },
                            enabled = !isRefining,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        when (draft.intent) {
                            CaptureIntent.EXPENSE, CaptureIntent.INCOME -> {
                                OutlinedTextField(
                                    draft.amount,
                                    { draft = draft.copy(amount = it.filter { c -> c.isDigit() || c == '.' }) },
                                    label = { Text("金额，可稍后补充") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    enabled = !isRefining,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    draft.title,
                                    { draft = draft.copy(title = it) },
                                    label = { Text(if (draft.intent == CaptureIntent.INCOME) "收入来源或备注" else "这笔钱花在什么地方？") },
                                    enabled = !isRefining,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    draft.category,
                                    { draft = draft.copy(category = it) },
                                    label = { Text(if (draft.intent == CaptureIntent.INCOME) "收入分类，可留空" else "分类，可留空") },
                                    enabled = !isRefining,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        draft.date,
                                        { draft = draft.copy(date = it) },
                                        label = { Text("发生日期，可留空") },
                                        enabled = !isRefining,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        draft.time,
                                        { draft = draft.copy(time = it) },
                                        label = { Text("时间，可留空") },
                                        enabled = !isRefining,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            CaptureIntent.JOURNAL -> {
                                OutlinedTextField(draft.title, { draft = draft.copy(title = it) }, label = { Text("给这段随笔起个标题") }, enabled = !isRefining, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(draft.content, { draft = draft.copy(content = it) }, label = { Text("写下此刻，可稍后补充") }, minLines = 5, enabled = !isRefining, modifier = Modifier.fillMaxWidth())
                            }
                            CaptureIntent.SCHEDULE -> {
                                OutlinedTextField(draft.title, { draft = draft.copy(title = it) }, label = { Text("要做什么？") }, enabled = !isRefining, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(draft.date, { draft = draft.copy(date = it) }, label = { Text("日期") }, enabled = !isRefining, modifier = Modifier.weight(1f))
                                    OutlinedTextField(draft.time, { draft = draft.copy(time = it) }, label = { Text("时间") }, enabled = !isRefining, modifier = Modifier.weight(1f))
                                }
                            }
                            CaptureIntent.CHAT -> {
                                chatMessages.forEach { message ->
                                    Surface(
                                        color = if (message.fromUser) SageSoft else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(message.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                OutlinedTextField(
                                    draft.content,
                                    { draft = draft.copy(content = it) },
                                    label = { Text("想和 Milo 聊什么？") },
                                    minLines = 3,
                                    enabled = !isRefining,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (status.isNotBlank()) Text(status, color = Sage, style = MaterialTheme.typography.labelSmall)
                        Button(
                            onClick = {
                                when (draft.intent) {
                                    CaptureIntent.EXPENSE, CaptureIntent.INCOME -> {
                                        val amount = MoneyCents.fromYuan(draft.amount)
                                        val occurredAt = draft.occurredAtMillis()
                                        when {
                                            amount.value <= 0 -> status = "金额还没填写，先留空也可以稍后补充"
                                            occurredAt == null -> status = "日期或时间格式不正确，请检查后再保存"
                                            else -> {
                                                isSaving = true
                                                scope.launch {
                                                    try {
                                                        addTransaction(
                                                            amount,
                                                            if (draft.intent == CaptureIntent.INCOME) TransactionType.INCOME else TransactionType.EXPENSE,
                                                            categoryId = draft.category,
                                                            note = draft.title,
                                                            occurredAt = occurredAt,
                                                            source = draft.source,
                                                        )
                                                        draft = MiloDraft(source = CaptureSource.TEXT_AI)
                                                        status = "已保存到账本，可以继续说下一件事"
                                                    } catch (_: Exception) {
                                                        status = "保存失败，请检查存储空间后重试"
                                                    } finally {
                                                        isSaving = false
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    CaptureIntent.JOURNAL -> {
                                        if (lifeStore.addJournal(draft.title, draft.content.ifBlank { draft.rawText }, emptyList()) != null) {
                                            draft = MiloDraft(source = CaptureSource.TEXT_AI)
                                            status = "随笔已保存，可以在随笔页继续添加照片"
                                        } else {
                                            status = "先补充一点文字，再保存这段随笔"
                                        }
                                    }
                                    CaptureIntent.SCHEDULE -> {
                                        if (lifeStore.addSchedule(draft.title, draft.date, draft.time) != null) {
                                            draft = MiloDraft(source = CaptureSource.TEXT_AI)
                                            status = "日程已保存，可以在日程页继续编辑"
                                        } else {
                                            status = "先补充要做的事情，再保存日程"
                                        }
                                    }
                                    CaptureIntent.CHAT -> {
                                        val message = draft.content.trim().ifBlank { draft.rawText.trim() }
                                        if (message.isBlank()) {
                                            status = "想和 Milo 聊什么？"
                                        } else {
                                            val history = chatMessages.map { turn ->
                                                MiloChatTurn(
                                                    role = if (turn.fromUser) MiloChatRole.USER else MiloChatRole.ASSISTANT,
                                                    content = turn.text,
                                                )
                                            }
                                            chatMessages += ChatMessage(message, fromUser = true)
                                            draft = draft.copy(rawText = "", content = "")
                                            isRefining = true
                                            status = "Milo 正在回复…"
                                            scope.launch {
                                                when (val result = aiClient.chat(message, history)) {
                                                    is MiloAiResult.Reply -> {
                                                        chatMessages += ChatMessage(result.text, fromUser = false)
                                                        status = ""
                                                    }
                                                    is MiloAiResult.Failure -> status = result.reason
                                                    is MiloAiResult.Clarification -> status = result.question
                                                    is MiloAiResult.Capture -> Unit
                                                }
                                                isRefining = false
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isRefining && !isSaving,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        ) { Text(if (draft.intent == CaptureIntent.CHAT) "发送给 Milo" else "确认并整理") }
                    }
                }
            }
        }
    }
}

private fun CaptureIntent.label() = when (this) {
    CaptureIntent.EXPENSE -> "记账"
    CaptureIntent.INCOME -> "收入"
    CaptureIntent.JOURNAL -> "随笔"
    CaptureIntent.SCHEDULE -> "日程"
    CaptureIntent.CHAT -> "闲聊"
}

private fun captureIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
}

@Composable
fun HistoryRoute(
    transactions: List<Transaction>,
    modifier: Modifier = Modifier,
    onEdit: (Transaction) -> Unit = {},
) {
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showAllMonths by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf<TransactionType?>(null) }
    var previewImageUri by remember { mutableStateOf<String?>(null) }
    val periodTransactions = if (showAllMonths) {
        transactions
    } else {
        transactions.filter { transaction ->
            YearMonth.from(Instant.ofEpochMilli(transaction.occurredAt).atZone(ZoneId.systemDefault())) == selectedMonth
        }
    }
    val zoneId = remember { ZoneId.systemDefault() }
    val visibleTransactions = periodTransactions.filter { transaction ->
        (selectedType == null || transaction.type == selectedType) &&
            (selectedDate == null || Instant.ofEpochMilli(transaction.occurredAt).atZone(zoneId).toLocalDate() == selectedDate)
    }
    val calendarDays = remember(transactions, selectedMonth, zoneId) {
        buildHistoryCalendar(selectedMonth, transactions, zoneId)
    }
    val expense = periodTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
    val income = periodTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("生活时间线", style = MaterialTheme.typography.headlineLarge)
            Text("每一笔生活，都值得被好好看见。", color = MutedInk)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                val periodLabel = if (showAllMonths) "累计" else "所选月份"
                InsightCard("支出", "¥" + formatCents(expense), CoralSoft, Coral, Modifier.weight(1f), periodLabel)
                InsightCard("收入", "¥" + formatCents(income), SageSoft, Sage, Modifier.weight(1f), periodLabel)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (!showAllMonths) TextButton(onClick = { selectedMonth = selectedMonth.minusMonths(1); selectedDate = null }) { Text("‹") }
                Text(
                    if (showAllMonths) "全部月份" else "${selectedMonth.year} 年 ${selectedMonth.monthValue} 月",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (!showAllMonths) TextButton(onClick = { selectedMonth = selectedMonth.plusMonths(1); selectedDate = null }) { Text("›") }
                FilterChip(selected = showAllMonths, onClick = { showAllMonths = !showAllMonths; selectedDate = null }, label = { Text("全部") })
            }
        }
        if (!showAllMonths) {
            item {
                HistoryCalendar(
                    days = calendarDays,
                    selectedDate = selectedDate,
                    onDayClick = { selectedDate = if (selectedDate == it) null else it },
                )
            }
            if (selectedDate != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${selectedDate!!.monthValue} 月 ${selectedDate!!.dayOfMonth} 日", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { selectedDate = null }) { Text("查看整月") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = { Text("全部") }, modifier = Modifier.weight(1f))
                FilterChip(selected = selectedType == TransactionType.EXPENSE, onClick = { selectedType = TransactionType.EXPENSE }, label = { Text("支出") }, modifier = Modifier.weight(1f))
                FilterChip(selected = selectedType == TransactionType.INCOME, onClick = { selectedType = TransactionType.INCOME }, label = { Text("收入") }, modifier = Modifier.weight(1f))
            }
        }
        if (transactions.isEmpty()) {
            item {
                DoodleEmptyState("你的生活时间线还很安静", "从首页记下第一笔，Milo 会替你整理成一段段回忆。", kind = "history")
            }
        } else if (visibleTransactions.isEmpty()) {
            item {
                DoodleEmptyState(
                    if (selectedDate != null) "这一天还没有记录" else "这个范围还没有记录",
                    if (selectedDate != null) "点“查看整月”回到当月全部账目。" else "切换月份或查看全部记录。",
                    kind = "history",
                )
            }
        } else {
            items(visibleTransactions, key = { it.id }) { transaction ->
                HistoryItem(transaction, onPhotoClick = { previewImageUri = it }, onEdit = onEdit)
            }
        }
    }
    previewImageUri?.let { uri ->
        JournalPhotoDialog(listOf(uri), initial = 0) { previewImageUri = null }
    }
}

@Composable
private fun HistoryCalendar(
    days: List<HistoryCalendarDay?>,
    selectedDate: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("每日支出", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { weekday ->
                    Text(
                        weekday,
                        color = MutedInk,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.weight(1f).height(50.dp).padding(2.dp))
                        } else {
                            val isToday = day.date == today
                            val isSelected = day.date == selectedDate
                            val background = when {
                                isToday -> Coral
                                day.expenseCents > 0 -> CoralSoft
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val foreground = if (isToday) Color.White else Ink
                            Surface(
                                onClick = { onDayClick(day.date) },
                                color = background,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isSelected) BorderStroke(1.dp, Sage) else null,
                                modifier = Modifier.weight(1f).height(50.dp).padding(2.dp)
                                    .semantics {
                                        contentDescription = "${day.date.monthValue}月${day.date.dayOfMonth}日，支出${formatCents(day.expenseCents)}元"
                                    },
                            ) {
                                Column(
                                    Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(day.date.dayOfMonth.toString(), color = foreground, style = MaterialTheme.typography.labelSmall)
                                    if (day.expenseCents > 0 || isToday) {
                                        Text(
                                            formatCents(day.expenseCents),
                                            color = foreground,
                                            fontSize = 7.sp,
                                            maxLines = 2,
                                            softWrap = true,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCard(label: String, value: String, bg: Color, fg: Color, modifier: Modifier, periodLabel: String) {
    Surface(color = bg, shape = RoundedCornerShape(22.dp), modifier = modifier) { Column(Modifier.padding(16.dp)) { Text(label, color = fg, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(6.dp)); Text(value, color = fg, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(periodLabel, color = fg.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall) } }
}

@Composable
private fun HistoryItem(
    transaction: Transaction,
    onPhotoClick: (String) -> Unit,
    onEdit: (Transaction) -> Unit,
) {
    val formatter = remember { SimpleDateFormat("MM 月 dd 日  HH:mm", Locale.getDefault()) }
    val photoUri = transaction.imageUri
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            MiloIcon(if (transaction.type == TransactionType.EXPENSE) "wallet" else "spark", Modifier.size(38.dp), selected = true, tint = if (transaction.type == TransactionType.EXPENSE) Coral else Sage)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(transaction.note ?: "未命名记录", style = MaterialTheme.typography.titleMedium)
                Text(formatter.format(Date(transaction.occurredAt)), color = MutedInk, style = MaterialTheme.typography.bodySmall)
                if (photoUri != null) {
                    Text("查看账目照片", color = Sage, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { onPhotoClick(photoUri) })
                }
                TextButton(onClick = { onEdit(transaction) }) { Text("编辑记录") }
            }
            if (photoUri != null) {
                val photo = rememberImageBitmap(photoUri, maxDimension = 240)
                if (photo != null) {
                    Image(
                        photo,
                        contentDescription = "查看账目照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(42.dp).clickable { onPhotoClick(photoUri) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            Text((if (transaction.type == TransactionType.EXPENSE) "-" else "+") + formatCents(transaction.amountCents), color = if (transaction.type == TransactionType.EXPENSE) Coral else Sage, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun JournalPhotoStack(uris: List<String>, onClick: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.CenterStart) {
        val visible = uris.take(4)
        val spread = ((maxWidth - 108.dp) / (visible.size - 1).coerceAtLeast(1)).coerceAtMost(58.dp)
        visible.forEachIndexed { index, uri ->
            val bitmap = rememberImageBitmap(uri, maxDimension = 600)
            if (bitmap != null) {
                Surface(
                    onClick = { onClick(index) },
                    color = Color.White,
                    border = BorderStroke(1.dp, Ink),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(width = 108.dp, height = 138.dp)
                        .offset(x = spread * index),
                ) {
                    Box {
                        Image(bitmap, contentDescription = "随笔照片 ${index + 1}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(5.dp))
                        if (index == visible.lastIndex && uris.size > visible.size) {
                            Surface(color = Ink, shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                                Text("+" + (uris.size - visible.size), color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberImageBitmap(uri: String, maxDimension: Int = 1800): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri, maxDimension) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val parsedUri = Uri.parse(uri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(parsedUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sampleSize = 1
                while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(parsedUri)?.use {
                    BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return bitmap
}

@Composable
internal fun JournalPhotoDialog(uris: List<String>, initial: Int, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = initial, pageCount = { uris.size })
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val bitmap = rememberImageBitmap(uris[page], maxDimension = 2400)
                if (bitmap != null) {
                    Image(bitmap, contentDescription = "照片 ${page + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("照片暂时无法打开", color = Color.White)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((pager.currentPage + 1).toString() + " / " + uris.size, color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("关闭", color = Color.White) }
            }
            }
        }
    }
}

@Composable
fun ScheduleRoute(lifeStore: LifeStore, modifier: Modifier = Modifier) {
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var editingScheduleId by remember { mutableStateOf<String?>(null) }
    var deletingSchedule by remember { mutableStateOf<ScheduleRecord?>(null) }
    var formStatus by remember { mutableStateOf("") }
    val schedules by lifeStore.schedules.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = modifier.fillMaxSize()
            .imePadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        state = listState,
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("日程胶囊", style = MaterialTheme.typography.headlineLarge); Text("让 Milo 帮你记住那些想做的事。", color = MutedInk) }
                MiloIcon("calendar", Modifier.size(32.dp), selected = true, tint = Sage)
            }
        }
        item {
            Surface(color = Gold.copy(alpha = .16f), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("日程由首页 Milo 统一添加", style = MaterialTheme.typography.titleMedium)
                        Text("说出时间和要做的事，确认后再回来编辑。", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(title, { title = it }, label = { Text("手动补充事项") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(date, { date = it }, label = { Text("日期，可留空") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(time, { time = it }, label = { Text("时间，可留空") }, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                val currentId = editingScheduleId
                                val validTiming = isValidScheduleTiming(date, time)
                                val saved = if (validTiming) {
                                    if (currentId == null) {
                                        lifeStore.addSchedule(title, date, time)
                                    } else {
                                        lifeStore.updateSchedule(currentId, title, date, time)
                                    }
                                } else null
                                if (saved != null) {
                                    title = ""
                                    date = ""
                                    time = ""
                                    editingScheduleId = null
                                    formStatus = if (currentId == null) "已加入日程" else "修改已保存"
                                } else if (title.isNotBlank() && !validTiming) {
                                    formStatus = "日期或时间格式不正确，请使用 yyyy-MM-dd 和 HH:mm"
                                } else {
                                    formStatus = "请填写要做的事情后保存"
                                }
                            },
                            enabled = title.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (editingScheduleId == null) "加入日程" else "保存修改") }
                        if (editingScheduleId != null) {
                            TextButton(
                                onClick = {
                                    title = ""
                                    date = ""
                                    time = ""
                                    editingScheduleId = null
                                    formStatus = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("取消修改") }
                        }
                        if (formStatus.isNotBlank()) {
                            Text(formStatus, color = MutedInk, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    MiloIllustration("schedule", Modifier.size(92.dp), tint = Ink)
                }
            }
        }
        item { Text("今天和接下来", style = MaterialTheme.typography.titleLarge) }
        if (schedules.isEmpty()) {
            item { DoodleEmptyState("还没有日程", "从首页对 Milo 说“提醒我周六买花”即可添加。", kind = "schedule") }
        } else items(schedules, key = { it.id }) { event ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiloIcon("calendar", Modifier.size(22.dp), selected = true, tint = Sage)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(event.title, style = MaterialTheme.typography.bodyLarge)
                        val timing = listOf(event.date, event.time).filter { it.isNotBlank() }.joinToString("  ").ifBlank { "时间未定" }
                        Text(timing, color = MutedInk, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        editingScheduleId = event.id
                        title = event.title
                        date = event.date
                        time = event.time
                        formStatus = ""
                        scope.launch { listState.animateScrollToItem(1) }
                    }) { Text("编辑") }
                    TextButton(onClick = { deletingSchedule = event }) { Text("删除") }
                }
            }
        }
    }
    deletingSchedule?.let { event ->
        AlertDialog(
            onDismissRequest = { deletingSchedule = null },
            title = { Text("删除这条日程？") },
            text = { Text("“${event.title}”将从日程中移除。") },
            confirmButton = {
                TextButton(onClick = {
                    lifeStore.deleteSchedule(event.id)
                    if (editingScheduleId == event.id) {
                        editingScheduleId = null
                        title = ""
                        date = ""
                        time = ""
                    }
                    deletingSchedule = null
                    formStatus = "日程已删除"
                }) { Text("删除日程") }
            },
            dismissButton = { TextButton(onClick = { deletingSchedule = null }) { Text("保留") } },
        )
    }
}

@Composable
fun MiloIcon(kind: String, modifier: Modifier = Modifier, selected: Boolean = false, tint: Color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * .105f)
        val w = size.width; val h = size.height
        when (kind) {
            "mic" -> { drawRoundRect(tint, Offset(w * .34f, h * .12f), androidx.compose.ui.geometry.Size(w * .32f, h * .53f), cornerRadius = CornerRadius(w * .16f), style = stroke); drawArc(tint, 0f, 180f, false, Offset(w * .18f, h * .26f), androidx.compose.ui.geometry.Size(w * .64f, h * .57f), style = stroke); drawLine(tint, Offset(w * .5f, h * .83f), Offset(w * .5f, h * .98f), strokeWidth = stroke.width); drawLine(tint, Offset(w * .34f, h * .98f), Offset(w * .66f, h * .98f), strokeWidth = stroke.width) }
            "stop" -> drawRoundRect(tint, Offset(w * .25f, h * .25f), androidx.compose.ui.geometry.Size(w * .5f, h * .5f), cornerRadius = CornerRadius(w * .08f))
            "send" -> { val path = Path().apply { moveTo(w * .12f, h * .48f); lineTo(w * .88f, h * .12f); lineTo(w * .62f, h * .88f); lineTo(w * .48f, h * .58f); close() }; drawPath(path, tint, style = stroke); drawLine(tint, Offset(w * .48f, h * .58f), Offset(w * .88f, h * .12f), strokeWidth = stroke.width) }
            "back" -> { drawLine(tint, Offset(w * .75f, h * .5f), Offset(w * .2f, h * .5f), strokeWidth = stroke.width); drawLine(tint, Offset(w * .2f, h * .5f), Offset(w * .45f, h * .25f), strokeWidth = stroke.width); drawLine(tint, Offset(w * .2f, h * .5f), Offset(w * .45f, h * .75f), strokeWidth = stroke.width) }
            "spark" -> { drawCircle(tint.copy(alpha = .18f), w * .46f, Offset(w * .5f, h * .5f)); drawLine(tint, Offset(w * .5f, h * .08f), Offset(w * .5f, h * .28f), strokeWidth = stroke.width); drawLine(tint, Offset(w * .5f, h * .72f), Offset(w * .5f, h * .92f), strokeWidth = stroke.width); drawLine(tint, Offset(w * .08f, h * .5f), Offset(w * .28f, h * .5f), strokeWidth = stroke.width); drawLine(tint, Offset(w * .72f, h * .5f), Offset(w * .92f, h * .5f), strokeWidth = stroke.width); drawCircle(tint, w * .13f, Offset(w * .5f, h * .5f)) }
            "wallet" -> { drawRoundRect(tint, Offset(w * .12f, h * .25f), androidx.compose.ui.geometry.Size(w * .76f, h * .54f), cornerRadius = CornerRadius(w * .1f), style = stroke); drawLine(tint, Offset(w * .12f, h * .38f), Offset(w * .86f, h * .38f), strokeWidth = stroke.width); drawCircle(tint, w * .055f, Offset(w * .68f, h * .54f)) }
            "calendar" -> { drawRoundRect(tint, Offset(w * .14f, h * .18f), androidx.compose.ui.geometry.Size(w * .72f, h * .68f), cornerRadius = CornerRadius(w * .08f), style = stroke); drawLine(tint, Offset(w * .14f, h * .4f), Offset(w * .86f, h * .4f), strokeWidth = stroke.width); drawLine(tint, Offset(w * .32f, h * .1f), Offset(w * .32f, h * .28f), strokeWidth = stroke.width); drawLine(tint, Offset(w * .68f, h * .1f), Offset(w * .68f, h * .28f), strokeWidth = stroke.width); drawCircle(tint, w * .04f, Offset(w * .35f, h * .58f)); drawCircle(tint, w * .04f, Offset(w * .55f, h * .58f)) }
            "note" -> { drawRoundRect(tint, Offset(w * .18f, h * .12f), androidx.compose.ui.geometry.Size(w * .62f, h * .76f), cornerRadius = CornerRadius(w * .06f), style = stroke); drawLine(tint, Offset(w * .34f, h * .36f), Offset(w * .65f, h * .36f), strokeWidth = stroke.width); drawLine(tint, Offset(w * .34f, h * .56f), Offset(w * .65f, h * .56f), strokeWidth = stroke.width) }
            "history" -> {
                // A compact timeline reads clearly at the small navigation size.
                drawLine(tint, Offset(w * .25f, h * .18f), Offset(w * .25f, h * .82f), strokeWidth = stroke.width)
                listOf(.28f, .5f, .72f).forEach { y ->
                    drawCircle(tint, radius = w * .065f, center = Offset(w * .25f, h * y))
                    drawLine(tint, Offset(w * .4f, h * y), Offset(w * .82f, h * y), strokeWidth = stroke.width)
                }
            }
            "home" -> { val path = Path().apply { moveTo(w * .12f, h * .48f); lineTo(w * .5f, h * .14f); lineTo(w * .88f, h * .48f); moveTo(w * .23f, h * .42f); lineTo(w * .23f, h * .86f); lineTo(w * .77f, h * .86f); lineTo(w * .77f, h * .42f) }; drawPath(path, tint, style = stroke) }
            "settings" -> { drawCircle(tint, w * .32f, Offset(w * .5f, h * .5f), style = stroke); drawCircle(tint, w * .08f, Offset(w * .5f, h * .5f)); for (i in 0..7) { val a = i * Math.PI / 4; drawLine(tint, Offset((w * .5f + w * .36f * kotlin.math.cos(a)).toFloat(), (h * .5f + h * .36f * kotlin.math.sin(a)).toFloat()), Offset((w * .5f + w * .48f * kotlin.math.cos(a)).toFloat(), (h * .5f + h * .48f * kotlin.math.sin(a)).toFloat()), strokeWidth = stroke.width) } }
        }
    }
}

@Composable
private fun DoodleEmptyState(title: String, caption: String, kind: String = "spark") {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            MiloIllustration(kind, Modifier.size(86.dp), tint = Ink)
            Spacer(Modifier.width(12.dp)); Column { Text(title, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(4.dp)); Text(caption, color = MutedInk, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun MiloIllustration(kind: String, modifier: Modifier = Modifier, tint: Color = Ink) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = (size.minDimension * .055f).coerceAtLeast(2.2f))
        val soft = tint.copy(alpha = .10f)
        when (kind) {
            "history" -> {
                // Stacked memory cards and a connecting line match the timeline content.
                drawRoundRect(soft, Offset(w * .4f, h * .18f), androidx.compose.ui.geometry.Size(w * .47f, h * .66f), cornerRadius = CornerRadius(w * .06f), style = stroke)
                drawRoundRect(tint, Offset(w * .31f, h * .11f), androidx.compose.ui.geometry.Size(w * .53f, h * .69f), cornerRadius = CornerRadius(w * .06f), style = stroke)
                drawLine(tint, Offset(w * .2f, h * .15f), Offset(w * .2f, h * .85f), strokeWidth = stroke.width)
                listOf(.24f, .5f, .76f).forEach { y ->
                    drawCircle(tint, radius = w * .045f, center = Offset(w * .2f, h * y))
                    drawLine(tint, Offset(w * .32f, h * y), Offset(w * .78f, h * y), strokeWidth = stroke.width)
                }
                drawLine(tint, Offset(w * .42f, h * .31f), Offset(w * .68f, h * .31f), strokeWidth = stroke.width)
                drawLine(tint, Offset(w * .42f, h * .58f), Offset(w * .62f, h * .58f), strokeWidth = stroke.width)
            }
            "schedule" -> {
                drawRoundRect(tint, Offset(w * .12f, h * .24f), androidx.compose.ui.geometry.Size(w * .68f, h * .56f), cornerRadius = CornerRadius(w * .08f), style = stroke)
                drawLine(tint, Offset(w * .12f, h * .43f), Offset(w * .8f, h * .43f), strokeWidth = stroke.width)
                drawLine(tint, Offset(w * .29f, h * .15f), Offset(w * .29f, h * .32f), strokeWidth = stroke.width)
                drawLine(tint, Offset(w * .63f, h * .15f), Offset(w * .63f, h * .32f), strokeWidth = stroke.width)
                drawCircle(tint, radius = w * .045f, center = Offset(w * .3f, h * .59f))
                drawCircle(tint, radius = w * .045f, center = Offset(w * .5f, h * .59f))
                drawCircle(tint, radius = w * .045f, center = Offset(w * .7f, h * .59f))
                drawLine(tint, Offset(w * .82f, h * .2f), Offset(w * .92f, h * .1f), strokeWidth = stroke.width)
                drawLine(tint, Offset(w * .92f, h * .1f), Offset(w * .87f, h * .25f), strokeWidth = stroke.width)
            }
            "companion" -> {
                drawCircle(soft, radius = w * .34f, center = Offset(w * .5f, h * .5f))
                drawCircle(tint, radius = w * .155f, center = Offset(w * .5f, h * .27f), style = stroke)
                val figure = Path().apply {
                    moveTo(w * .43f, h * .39f)
                    lineTo(w * .43f, h * .45f)
                    cubicTo(w * .31f, h * .48f, w * .24f, h * .56f, w * .24f, h * .69f)
                    lineTo(w * .24f, h * .82f)
                    lineTo(w * .76f, h * .82f)
                    lineTo(w * .76f, h * .69f)
                    cubicTo(w * .76f, h * .56f, w * .69f, h * .48f, w * .57f, h * .45f)
                    lineTo(w * .57f, h * .39f)
                    moveTo(w * .24f, h * .62f)
                    lineTo(w * .13f, h * .72f)
                    moveTo(w * .76f, h * .62f)
                    lineTo(w * .87f, h * .72f)
                }
                drawPath(figure, tint, style = stroke)
                drawCircle(tint, radius = w * .025f, center = Offset(w * .45f, h * .27f))
                drawCircle(tint, radius = w * .025f, center = Offset(w * .55f, h * .27f))
                drawArc(
                    tint,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * .45f, h * .29f),
                    size = androidx.compose.ui.geometry.Size(w * .1f, h * .06f),
                    style = stroke,
                )
            }
            "journal" -> {
                drawRoundRect(tint, Offset(w * .2f, h * .14f), androidx.compose.ui.geometry.Size(w * .52f, h * .72f), cornerRadius = CornerRadius(w * .05f), style = stroke)
                drawLine(tint, Offset(w * .34f, h * .37f), Offset(w * .6f, h * .37f), strokeWidth = stroke.width)
                drawLine(tint, Offset(w * .34f, h * .54f), Offset(w * .6f, h * .54f), strokeWidth = stroke.width)
                drawLine(tint, Offset(w * .54f, h * .86f), Offset(w * .54f, h * .66f), strokeWidth = stroke.width)
                drawOval(tint, Offset(w * .42f, h * .65f), androidx.compose.ui.geometry.Size(w * .18f, h * .12f), style = stroke)
                drawLine(tint, Offset(w * .54f, h * .7f), Offset(w * .67f, h * .55f), strokeWidth = stroke.width)
            }
            "settings" -> {
                drawCircle(soft, radius = w * .36f, center = Offset(w * .5f, h * .5f))
                listOf(.3f, .5f, .7f).forEachIndexed { index, y ->
                    drawLine(tint, Offset(w * .18f, h * y), Offset(w * .82f, h * y), strokeWidth = stroke.width)
                    val x = if (index % 2 == 0) .38f else .64f
                    drawCircle(tint, radius = w * .07f, center = Offset(w * x, h * y), style = stroke)
                }
            }
            else -> {
                drawCircle(soft, radius = w * .36f, center = Offset(w * .5f, h * .5f))
                drawArc(tint, 180f, 180f, false, Offset(w * .2f, h * .3f), androidx.compose.ui.geometry.Size(w * .6f, h * .45f), style = stroke)
                drawLine(tint, Offset(w * .26f, h * .53f), Offset(w * .74f, h * .53f), strokeWidth = stroke.width)
            }
        }
    }
}

private fun formatCents(cents: Long): String = MoneyCents(cents).toDisplayString()
