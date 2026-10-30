package com.malaxg.miloagent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("milo_settings", 0) }
    var provider by remember { mutableStateOf(prefs.getString("provider", "DeepSeek") ?: "DeepSeek") }
    var baseUrl by remember { mutableStateOf(prefs.getString("base_url", "https://api.deepseek.com/v1") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", "deepseek-chat") ?: "") }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var saved by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text("设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("AI 助手", style = MaterialTheme.typography.headlineLarge)
        Text("把模型接到你自己的 API，数据只从本机直连服务商。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("服务商预设", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DeepSeek", "通义", "智谱", "自定义").forEach { item ->
                        FilterChip(selected = provider == item, onClick = {
                            provider = item
                            when (item) {
                                "DeepSeek" -> { baseUrl = "https://api.deepseek.com/v1"; model = "deepseek-chat" }
                                "通义" -> { baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"; model = "qwen-plus" }
                                "智谱" -> { baseUrl = "https://open.bigmodel.cn/api/paas/v4"; model = "glm-4-flash" }
                            }
                        }, label = { Text(item) })
                    }
                }
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it },
                    label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = model, onValueChange = { model = it },
                    label = { Text("模型名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("API Key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text("密钥仅保存在本机偏好设置中，不会上传到 milo。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = {
            prefs.edit().putString("provider", provider).putString("base_url", baseUrl)
                .putString("model", model).putString("api_key", apiKey).apply()
            saved = true
        }, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "已保存" else "保存设置") }
        Spacer(Modifier.height(24.dp))
        Text("交互偏好", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("语音识别", style = MaterialTheme.typography.titleMedium)
                Text("语音只用于把一句话整理成待确认草稿，确认后才会写入账本。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("隐私模式：本地优先", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
