package com.malaxg.miloagent

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.malaxg.milo.core.common.MoneyCents
import com.malaxg.milo.core.common.toDisplayString
import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.core.domain.model.TransactionType
import com.malaxg.milo.core.domain.repository.LedgerRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun TransactionEditRoute(
    transaction: Transaction,
    repository: LedgerRepository,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val originalTime = remember(transaction.id) {
        Instant.ofEpochMilli(transaction.occurredAt).atZone(ZoneId.systemDefault())
    }
    val originalDateText = remember(transaction.id) { originalTime.toLocalDate().toString() }
    val originalClockText = remember(transaction.id) {
        "%02d:%02d".format(originalTime.hour, originalTime.minute)
    }
    var type by remember(transaction.id) { mutableStateOf(transaction.type) }
    var amount by remember(transaction.id) { mutableStateOf(MoneyCents(transaction.amountCents).toDisplayString()) }
    var note by remember(transaction.id) { mutableStateOf(transaction.note.orEmpty()) }
    var category by remember(transaction.id) { mutableStateOf(transaction.categoryId.orEmpty()) }
    var date by remember(transaction.id) { mutableStateOf(originalDateText) }
    var time by remember(transaction.id) { mutableStateOf(originalClockText) }
    var imageUri by remember(transaction.id) { mutableStateOf(transaction.imageUri) }
    var status by remember(transaction.id) { mutableStateOf("") }
    var saving by remember(transaction.id) { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            imageUri = uri.toString()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding()
            .background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("‹ 历史") }
                Text("修改记录", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("检查并修改这笔记录", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransactionType.entries.forEach { choice ->
                            FilterChip(
                                selected = type == choice, onClick = { type = choice },
                                label = { Text(if (choice == TransactionType.EXPENSE) "支出" else "收入") },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("金额（元）") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(note, { note = it }, label = { Text("用途或备注") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(category, { category = it }, label = { Text("分类，可留空") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(date, { date = it }, label = { Text("发生日期 yyyy-MM-dd") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(time, { time = it }, label = { Text("发生时间 HH:mm") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(if (imageUri == null) "未附照片" else "已附一张照片", modifier = Modifier.weight(1f))
                        TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                            Text(if (imageUri == null) "添加照片" else "更换照片")
                        }
                        if (imageUri != null) {
                            TextButton(onClick = { imageUri = null }) { Text("移除照片") }
                        }
                    }
                    if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = {
                            val cents = MoneyCents.fromYuan(amount)
                            val occurredAt = if (date == originalDateText && time == originalClockText) {
                                transaction.occurredAt
                            } else MiloDraft(date = date, time = time).occurredAtMillis()
                            when {
                                cents.value <= 0 -> status = "请输入有效金额"
                                date.isBlank() || time.isBlank() -> status = "请填写发生日期和时间"
                                occurredAt == null -> status = "日期或时间格式不正确，请检查后再保存"
                                else -> {
                                    saving = true
                                    status = ""
                                    scope.launch {
                                        try {
                                            repository.update(transaction.copy(
                                                amountCents = cents.value, type = type,
                                                note = note.trim().takeIf { it.isNotEmpty() },
                                                categoryId = category.trim().takeIf { it.isNotEmpty() },
                                                occurredAt = occurredAt, imageUri = imageUri,
                                            ))
                                            onClose()
                                        } catch (_: Exception) {
                                            status = "保存失败，请重试"
                                        } finally {
                                            saving = false
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !saving, modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (saving) "正在保存…" else "保存修改") }
                }
            }
        }
    }
}
