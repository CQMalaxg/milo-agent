package com.malaxg.miloagent

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.malaxg.milo.core.designsystem.theme.Ink
import com.malaxg.milo.core.designsystem.theme.MutedInk
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class JournalEditorDraft(
    val title: String = "",
    val content: String = "",
    val imageUris: List<String> = emptyList(),
    val favorite: Boolean = false,
    val editingId: String? = null,
)

/** The composer and the social-style history are separate pages with shared draft state. */
@Composable
fun JournalRoute(lifeStore: LifeStore, modifier: Modifier = Modifier) {
    var editor by remember { mutableStateOf(JournalEditorDraft()) }
    var parkedNewDraft by remember { mutableStateOf<JournalEditorDraft?>(null) }
    var showingHistory by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var previewUris by remember { mutableStateOf<List<String>>(emptyList()) }
    val journals by lifeStore.journals.collectAsState()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        editor = editor.copy(imageUris = (editor.imageUris + uris.map { it.toString() }).distinct())
    }

    BackHandler(enabled = showingHistory) { showingHistory = false }
    if (showingHistory) {
        JournalHistoryPage(
            journals = journals,
            onWrite = { showingHistory = false },
            onEdit = { entry ->
                if (editor.editingId == null) parkedNewDraft = editor
                editor = JournalEditorDraft(entry.title, entry.content, entry.imageUris, entry.favorite, entry.id)
                showingHistory = false
            },
            onFavorite = { lifeStore.toggleJournalFavorite(it.id) },
            onPreview = { uris, index -> previewUris = uris; previewIndex = index },
            returnToEdit = editor.editingId != null,
            modifier = modifier,
        )
    } else {
        JournalWritePage(
            editor = editor,
            onEditorChange = { editor = it },
            onOpenHistory = { showingHistory = true },
            onChoosePhotos = { picker.launch(arrayOf("image/*")) },
            onPreview = { uris, index -> previewUris = uris; previewIndex = index },
            onSave = {
                val editingId = editor.editingId
                val saved = if (editingId == null) {
                    lifeStore.addJournal(editor.title, editor.content, editor.imageUris, editor.favorite)
                } else {
                    lifeStore.updateJournal(editingId, editor.title, editor.content, editor.imageUris, editor.favorite)
                }
                if (saved != null) {
                    editor = parkedNewDraft ?: JournalEditorDraft()
                    parkedNewDraft = null
                    showingHistory = true
                }
            },
            onCancelEdit = {
                editor = parkedNewDraft ?: JournalEditorDraft()
                parkedNewDraft = null
                showingHistory = true
            },
            modifier = modifier,
        )
    }
    if (previewIndex != null && previewUris.isNotEmpty()) {
        JournalPhotoDialog(previewUris, previewIndex ?: 0) {
            previewIndex = null
            previewUris = emptyList()
        }
    }
}

@Composable
private fun JournalWritePage(
    editor: JournalEditorDraft,
    onEditorChange: (JournalEditorDraft) -> Unit,
    onOpenHistory: () -> Unit,
    onChoosePhotos: () -> Unit,
    onPreview: (List<String>, Int) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding()
            .background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (editor.editingId == null) "写随笔" else "修改随笔", style = MaterialTheme.typography.headlineLarge)
                    Text("留住此刻的心情和小事。", color = MutedInk)
                }
                TextButton(onClick = onOpenHistory) { Text("查看历史") }
            }
        }
        item {
            Surface(color = Color.Transparent, border = BorderStroke(1.dp, Ink),
                shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("给今天留一点空白", style = MaterialTheme.typography.titleMedium)
                        Text("文字和照片，慢慢放进来。", color = MutedInk, style = MaterialTheme.typography.bodySmall)
                    }
                    Image(painter = painterResource(R.drawable.doodle_plant), contentDescription = "手绘植物插画",
                        colorFilter = ColorFilter.tint(Ink), modifier = Modifier.size(width = 100.dp, height = 78.dp))
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("标题", color = MutedInk, style = MaterialTheme.typography.labelMedium)
                BasicTextField(value = editor.title, onValueChange = { onEditorChange(editor.copy(title = it)) },
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(), decorationBox = { inner ->
                        Box {
                            if (editor.title.isBlank()) Text("给今天起个小标题", color = MutedInk, style = MaterialTheme.typography.titleLarge)
                            inner()
                        }
                    })
                HorizontalDivider(color = Ink.copy(alpha = .18f))
                Text("正文", color = MutedInk, style = MaterialTheme.typography.labelMedium)
                BasicTextField(value = editor.content, onValueChange = { onEditorChange(editor.copy(content = it)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    minLines = 6, modifier = Modifier.fillMaxWidth(), decorationBox = { inner ->
                        Box {
                            if (editor.content.isBlank()) Text("写下此刻，想到什么就写什么…", color = MutedInk, style = MaterialTheme.typography.bodyLarge)
                            inner()
                        }
                    })
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (editor.imageUris.isNotEmpty()) {
                    JournalPhotoStack(editor.imageUris) { onPreview(editor.imageUris, it) }
                    TextButton(onClick = { onEditorChange(editor.copy(imageUris = editor.imageUris.dropLast(1))) }) {
                        Text("移除最后一张照片")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    AssistChip(onClick = onChoosePhotos, label = { Text(if (editor.imageUris.isEmpty()) "添加照片" else "继续添加照片") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = editor.favorite,
                        onClick = { onEditorChange(editor.copy(favorite = !editor.favorite)) },
                        label = { Text(if (editor.favorite) "已收藏" else "收藏") })
                }
                Button(onClick = onSave,
                    enabled = editor.title.isNotBlank() || editor.content.isNotBlank() || editor.imageUris.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (editor.editingId == null) "保存随笔" else "保存修改")
                }
                if (editor.editingId != null) {
                    TextButton(onClick = onCancelEdit, modifier = Modifier.fillMaxWidth()) { Text("取消修改") }
                }
                Text("保存后可以在随笔历史中回看与修改。", color = MutedInk, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun JournalHistoryPage(
    journals: List<JournalRecord>,
    onWrite: () -> Unit,
    onEdit: (JournalRecord) -> Unit,
    onFavorite: (JournalRecord) -> Unit,
    onPreview: (List<String>, Int) -> Unit,
    returnToEdit: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("随笔历史", style = MaterialTheme.typography.headlineLarge)
                    Text(journals.size.toString() + " 条生活片段", color = MutedInk)
                }
                TextButton(onClick = onWrite) { Text(if (returnToEdit) "继续编辑" else "写一篇") }
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Ink.copy(alpha = .18f))
        }
        if (journals.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MiloIllustration("journal", Modifier.size(110.dp), tint = Ink)
                    Text("还没有随笔", style = MaterialTheme.typography.titleLarge)
                    Text("写下第一段心情，它会出现在这里。", color = MutedInk)
                    Button(onClick = onWrite) { Text("写第一篇") }
                }
            }
        } else {
            items(journals, key = { it.id }) { entry ->
                JournalFeedItem(entry, onEdit, onFavorite, onPreview)
            }
        }
    }
}

@Composable
private fun JournalFeedItem(
    entry: JournalRecord,
    onEdit: (JournalRecord) -> Unit,
    onFavorite: (JournalRecord) -> Unit,
    onPreview: (List<String>, Int) -> Unit,
) {
    val dateLabel = remember(entry.createdAt) {
        SimpleDateFormat("M 月 d 日 · HH:mm", Locale.getDefault()).format(Date(entry.createdAt))
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.Top) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = CircleShape,
                border = BorderStroke(1.dp, Ink), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    MiloIcon("note", Modifier.size(23.dp), selected = true, tint = Ink)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text("我的随笔", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(dateLabel, color = MutedInk, style = MaterialTheme.typography.labelSmall)
                }
                if (entry.title.isNotBlank()) Text(entry.title,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (entry.content.isNotBlank()) Text(entry.content,
                    color = Ink, style = MaterialTheme.typography.bodyLarge)
                if (entry.imageUris.isNotEmpty()) {
                    JournalPhotoStack(entry.imageUris) { onPreview(entry.imageUris, it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onEdit(entry) }) { Text("编辑") }
                    TextButton(onClick = { onFavorite(entry) }) { Text(if (entry.favorite) "已收藏" else "收藏") }
                }
            }
        }
        HorizontalDivider(color = Ink.copy(alpha = .12f))
    }
}
