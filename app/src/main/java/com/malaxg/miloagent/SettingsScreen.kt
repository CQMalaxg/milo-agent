package com.malaxg.miloagent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

private enum class SettingsSection { HOME, MODEL_ACCESS, VOICE, PRIVACY }

@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val profileStore = remember(context) { MiloModelProfileStore(context) }
    var section by remember { mutableStateOf(SettingsSection.HOME) }

    BackHandler(enabled = section != SettingsSection.HOME) { section = SettingsSection.HOME }

    when (section) {
        SettingsSection.HOME -> SettingsHome(
            profileStore = profileStore,
            onBack = onBack,
            onOpen = { section = it },
            modifier = modifier,
        )
        SettingsSection.MODEL_ACCESS -> ModelAccessSettings(
            profileStore = profileStore,
            onBack = { section = SettingsSection.HOME },
            modifier = modifier,
        )
        SettingsSection.VOICE -> SettingsInfoPage(
            title = "语音与输入",
            onBack = { section = SettingsSection.HOME },
            modifier = modifier,
        ) {
            val voiceAvailable = remember(context) {
                android.speech.SpeechRecognizer.isRecognitionAvailable(context)
            }
            SettingsInfoRow(
                title = "语音识别",
                detail = if (voiceAvailable) {
                    "系统语音识别服务可用。录音会先转成文字，发送或保存前仍可检查。"
                } else {
                    "当前设备没有系统语音识别服务。主页和账本仍可直接使用文字输入。"
                },
            )
        }
        SettingsSection.PRIVACY -> SettingsInfoPage(
            title = "隐私与数据",
            onBack = { section = SettingsSection.HOME },
            modifier = modifier,
        ) {
            SettingsInfoRow(
                title = "本地记录",
                detail = "账目、随笔和日程保存在此设备上。",
            )
            SettingsInfoRow(
                title = "模型请求",
                detail = "使用模型时，当前输入会由本机直接发送到所选服务商。",
            )
        }
    }
}

@Composable
private fun SettingsHome(
    profileStore: MiloModelProfileStore,
    onBack: () -> Unit,
    onOpen: (SettingsSection) -> Unit,
    modifier: Modifier,
) {
    val activeProfile = remember(profileStore) { profileStore.activeProfile() }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        SettingsHeader("设置", onBack)
        Spacer(Modifier.height(18.dp))
        SettingsEntry(
            icon = "spark",
            title = "Milo 模型接入",
            subtitle = activeProfile?.let { "当前使用 ${it.name} · ${it.model}" } ?: "添加并切换你的模型服务",
            onClick = { onOpen(SettingsSection.MODEL_ACCESS) },
        )
        Spacer(Modifier.height(10.dp))
        SettingsEntry(
            icon = "mic",
            title = "语音与输入",
            subtitle = "语音识别状态与文字输入方式",
            onClick = { onOpen(SettingsSection.VOICE) },
        )
        Spacer(Modifier.height(10.dp))
        SettingsEntry(
            icon = "note",
            title = "隐私与数据",
            subtitle = "了解本地记录和模型请求",
            onClick = { onOpen(SettingsSection.PRIVACY) },
        )
    }
}

@Composable
private fun ModelAccessSettings(
    profileStore: MiloModelProfileStore,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val aiClient = remember(context) { MiloAiClient(context) }
    val scope = rememberCoroutineScope()
    var profiles by remember(profileStore) { mutableStateOf(profileStore.profiles()) }
    var activeProfileId by remember(profileStore) { mutableStateOf(profileStore.activeProfile()?.id) }
    var formVisible by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var profileName by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("DeepSeek") }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var deleteCandidate by remember { mutableStateOf<MiloModelProfile?>(null) }

    fun startNewProfile() {
        editingId = null
        provider = "DeepSeek"
        profileName = "DeepSeek"
        baseUrl = "https://api.deepseek.com/v1"
        model = "deepseek-chat"
        apiKey = ""
        status = ""
        formVisible = true
    }

    fun editProfile(profile: MiloModelProfile) {
        editingId = profile.id
        profileName = profile.name
        provider = profile.provider
        baseUrl = profile.baseUrl
        model = profile.model
        apiKey = profile.apiKey
        status = ""
        formVisible = true
    }

    fun saveProfile(): Boolean {
        if (profileName.isBlank() || baseUrl.isBlank() || model.isBlank() || apiKey.isBlank()) {
            status = "请填写名称、Base URL、模型名称和 API Key"
            return false
        }
        if (!baseUrl.trim().startsWith("https://", ignoreCase = true)) {
            status = "Base URL 必须使用 HTTPS"
            return false
        }
        val profile = MiloModelProfile(
            id = editingId ?: UUID.randomUUID().toString(),
            name = profileName.trim(),
            provider = provider,
            baseUrl = baseUrl.trim(),
            model = model.trim(),
            apiKey = apiKey.trim(),
        )
        profileStore.save(profile)
        profiles = profileStore.profiles()
        activeProfileId = profile.id
        editingId = profile.id
        formVisible = false
        status = "模型已保存并启用"
        return true
    }

    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsHeader("Milo 模型接入", onBack)
        Text("已保存的模型", style = MaterialTheme.typography.titleMedium)
        if (profiles.isEmpty()) {
            Text("还没有模型。添加服务商信息并保存后，就会出现在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        profiles.forEach { profile ->
            val selected = profile.id == activeProfileId
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            profileStore.activate(profile.id)
                            activeProfileId = profile.id
                            status = "已切换到 ${profile.name}"
                        }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            Text("${profile.provider} · ${profile.model}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (selected) "使用中" else "切换", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { editProfile(profile) }) { Text("编辑") }
                        TextButton(onClick = { deleteCandidate = profile }) { Text("删除") }
                    }
                }
            }
        }
        Button(
            onClick = ::startNewProfile,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "添加模型配置" },
        ) { Text("添加模型") }

        if (formVisible) {
            Text(if (editingId == null) "添加模型" else "编辑模型", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("DeepSeek", "OpenAI", "通义", "智谱", "自定义").forEach { item ->
                    FilterChip(
                        selected = provider == item,
                        onClick = {
                            provider = item
                            if (item != "自定义") {
                                val preset = modelPreset(item)
                                baseUrl = preset.first
                                model = preset.second
                            }
                            if (editingId == null) profileName = item
                        },
                        label = { Text(item) },
                    )
                }
            }
            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it; status = "" },
                label = { Text("卡片名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; status = "" },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; status = "" },
                label = { Text("模型名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; status = "" },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("密钥保存在本机。切换卡片后，Milo 会使用所选模型发送请求。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = { formVisible = false; editingId = null; status = "" },
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }
                Button(onClick = { saveProfile() }, modifier = Modifier.weight(1f)) { Text("保存并启用") }
            }
            TextButton(
                onClick = {
                    if (saveProfile()) {
                        testing = true
                        status = "正在测试连接…"
                        scope.launch {
                            status = when (val result = aiClient.testConnection()) {
                                is MiloAiResult.Failure -> result.reason
                                else -> "连接成功"
                            }
                            testing = false
                        }
                    }
                },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (testing) "正在测试…" else "测试模型连接") }
        }
        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }

    deleteCandidate?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("删除模型") },
            text = { Text("删除“${profile.name}”的接入配置？") },
            confirmButton = {
                TextButton(onClick = {
                    profiles = profileStore.delete(profile.id)
                    activeProfileId = profileStore.activeProfile()?.id
                    if (editingId == profile.id) formVisible = false
                    deleteCandidate = null
                    status = "模型配置已删除"
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingsInfoPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsHeader(title, onBack)
        content()
    }
}

@Composable
private fun SettingsInfoRow(title: String, detail: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack) { Text("‹ 返回") }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SettingsEntry(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MiloIcon(icon, Modifier.size(24.dp), selected = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun modelPreset(provider: String): Pair<String, String> = when (provider) {
    "DeepSeek" -> "https://api.deepseek.com/v1" to "deepseek-chat"
    "OpenAI" -> "https://api.openai.com/v1" to "gpt-4o-mini"
    "通义" -> "https://dashscope.aliyuncs.com/compatible-mode/v1" to "qwen-plus"
    "智谱" -> "https://open.bigmodel.cn/api/paas/v4" to "glm-4-flash"
    else -> "" to ""
}
