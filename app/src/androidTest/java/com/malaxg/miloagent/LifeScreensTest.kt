package com.malaxg.miloagent

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.malaxg.milo.core.designsystem.theme.MiloagentTheme
import com.malaxg.milo.core.domain.model.CaptureSource
import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.core.domain.model.TransactionType
import com.malaxg.milo.core.domain.repository.LedgerRepository
import com.malaxg.milo.feature.ledger.LedgerRoute
import com.malaxg.milo.feature.ledger.LedgerScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.json.JSONArray
import java.io.File
import java.util.UUID

/** Exercises real screens on a short viewport using isolated records and settings. */
class LifeScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferenceName = "life_screens_test_" + UUID.randomUUID()
    private val context = object : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences(preferenceName, mode)
    }
    private val photos = mutableListOf<File>()
    private val store = LifeStore(context)

    @After
    fun cleanUp() {
        baseContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit().clear().commit()
        photos.forEach(File::delete)
    }

    @Test
    fun homeComposerAcceptsTextAndShowsVoiceAndSendActionsOnShortScreen() {
        val repository = TestLedgerRepository()
        compose.setContent {
            MiloagentTheme {
                Box(Modifier.size(360.dp, 380.dp)) {
                    HomeRoute(repository, store)
                }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("你好，Milo")
        compose.onNodeWithContentDescription("语音输入").assertIsDisplayed()
        compose.onNodeWithContentDescription("发送").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("语音输入").performClick()
        compose.onNodeWithContentDescription("按住说话").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithContentDescription("切换文字输入").performClick()
        compose.onAllNodes(hasSetTextAction())[0].assertIsDisplayed()
    }

    @Test
    fun homeExpenseCreatesConfirmableDraftThatSurvivesNavigationAndSavesToLedger() {
        val repository = TestLedgerRepository()
        val onHome = mutableStateOf(true)
        compose.setContent {
            MiloagentTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    if (onHome.value) HomeRoute(repository, store)
                    else androidx.compose.material3.Text("其他页面")
                }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("午饭花了24")
        compose.onNodeWithContentDescription("发送").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("待确认 · 记账")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("待确认 · 记账").assertIsDisplayed()
        compose.runOnIdle { assertTrue(repository.records.value.isEmpty()) }

        compose.runOnIdle { onHome.value = false }
        compose.onNodeWithText("其他页面").assertIsDisplayed()
        compose.runOnIdle { onHome.value = true }
        compose.onNodeWithText("待确认 · 记账").assertIsDisplayed()
        compose.onNodeWithText("原话：午饭花了24").assertIsDisplayed()
        compose.onNodeWithText("确认保存").performScrollTo().performClick()
        compose.waitUntil { repository.records.value.size == 1 }
        compose.runOnIdle {
            val record = repository.records.value.single()
            assertEquals(2400L, record.amountCents)
            assertEquals("午饭", record.note)
        }
    }

    @Test
    fun homeSessionsKeepPendingDraftsSeparateAndCanSwitchBack() {
        val repository = TestLedgerRepository()
        compose.setContent {
            MiloagentTheme {
                Box(Modifier.size(360.dp, 640.dp)) { HomeRoute(repository, store) }
            }
        }

        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("午饭花了24")
        compose.onNodeWithContentDescription("发送").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("待确认 · 记账")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("新建会话").performClick()
        compose.onNodeWithText("新对话").assertIsDisplayed()
        compose.onAllNodes(hasText("待确认 · 记账")).assertCountEquals(0)

        compose.onNodeWithText("新对话").performClick()
        compose.onNodeWithText("午饭花了24").performClick()
        compose.onNodeWithText("待确认 · 记账").assertIsDisplayed()
        compose.onNodeWithText("原话：午饭花了24").assertIsDisplayed()
        compose.runOnIdle { assertTrue(repository.records.value.isEmpty()) }
    }

    @Test
    fun pendingActionCardRequiresValidFieldsAndExplicitConfirmation() {
        var confirmed = false
        val draft = mutableStateOf(MiloDraft(intent = CaptureIntent.EXPENSE, title = "午饭", rawText = "午饭"))
        compose.setContent {
            MiloagentTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    HomeActionCard(
                        draft = draft.value,
                        onDraftChange = { draft.value = it },
                        onConfirm = { confirmed = true },
                        onCancel = {},
                        isSaving = false,
                    )
                }
            }
        }
        compose.onNodeWithText("确认保存").assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("32.50")
        compose.onNodeWithText("确认保存").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }

    @Test
    fun ledgerSaveAppearsInHistoryAndFiltersOnShortScreen() {
        val repository = TestLedgerRepository()
        val history = mutableStateOf(false)
        compose.setContent {
            MiloagentTheme {
                Box(Modifier.size(360.dp, 480.dp)) {
                    if (history.value) HistoryRoute(repository.records.value)
                    else LedgerRoute(repository)
                }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("12.34")
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextReplacement("Lunch test")
        compose.onNodeWithText("保存记录").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil { repository.records.value.size == 1 }
        compose.runOnIdle {
            assertEquals(1234L, repository.records.value.single().amountCents)
            assertEquals("Lunch test", repository.records.value.single().note)
            history.value = true
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Lunch test"))
        compose.onNodeWithText("Lunch test").assertIsDisplayed()
        compose.onAllNodes(hasText("收入") and hasClickAction())[0].performScrollTo().performClick()
        compose.onNodeWithText("这个范围还没有记录").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun ledgerRecordOffersPhotoAttachmentForThatSpecificTransaction() {
        val now = System.currentTimeMillis()
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            amountCents = 2400,
            note = "午饭",
            occurredAt = now,
            createdAt = now,
            source = CaptureSource.MANUAL,
        )
        var photoTarget: Transaction? = null
        compose.setContent {
            MiloagentTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    LedgerScreen(
                        transactions = listOf(transaction),
                        amountText = "", noteText = "",
                        selectedType = TransactionType.EXPENSE,
                        isListening = false, hasVoiceDraft = false,
                        isSaving = false, saveStatus = "", imageUri = null,
                        onAmountChange = {}, onNoteChange = {}, onTypeChange = {},
                        onVoiceClick = {}, onImageClick = {}, onImageRemove = {},
                        onQuickCapture = { _, _ -> }, onSubmit = {},
                        onTransactionPhotoClick = { photoTarget = it },
                    )
                }
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("午饭"))
        compose.onNodeWithContentDescription("为午饭添加照片").performClick()
        compose.runOnIdle { assertEquals(transaction, photoTarget) }
    }

    @Test
    fun ledgerPhotoOpensFromHistory() {
        var editRequested: Transaction? = null
        val file = File(baseContext.cacheDir, "ledger-photo-" + UUID.randomUUID() + ".png")
        photos += file
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff222222.toInt())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val now = System.currentTimeMillis()
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            amountCents = 3200,
            note = "午饭照片",
            occurredAt = now,
            createdAt = now,
            source = CaptureSource.MANUAL,
            imageUri = Uri.fromFile(file).toString(),
        )
        compose.setContent {
            MiloagentTheme {
                Box(Modifier.size(360.dp, 480.dp)) {
                    HistoryRoute(listOf(transaction), onEdit = { editRequested = it })
                }
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("查看账目照片"))
        compose.onNodeWithText("查看账目照片").performClick()
        compose.onNodeWithText("1 / 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("照片 1").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("编辑记录").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(transaction, editRequested) }
    }

    @Test
    fun savedLedgerRecordCanBeEditedWithoutChangingItsIdentityOrSource() {
        val repository = TestLedgerRepository()
        val now = System.currentTimeMillis()
        val original = Transaction(
            id = UUID.randomUUID().toString(), amountCents = 3200,
            note = "午饭", categoryId = "餐饮", occurredAt = now, createdAt = now,
            source = CaptureSource.VOICE, imageUri = "content://test/photo",
        )
        repository.records.value = listOf(original)
        val closed = mutableStateOf(false)
        compose.setContent {
            MiloagentTheme {
                Box(Modifier.size(360.dp, 480.dp)) {
                    TransactionEditRoute(original, repository, onClose = { closed.value = true })
                }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("45.67")
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextReplacement("补记午饭")
        compose.onNodeWithText("移除照片").performScrollTo().performClick()
        compose.onNodeWithText("保存修改").performScrollTo().performClick()
        compose.waitUntil { closed.value }
        compose.runOnIdle {
            val updated = repository.records.value.single()
            assertEquals(original.id, updated.id)
            assertEquals(original.createdAt, updated.createdAt)
            assertEquals(original.occurredAt, updated.occurredAt)
            assertEquals(CaptureSource.VOICE, updated.source)
            assertEquals(4567L, updated.amountCents)
            assertEquals("补记午饭", updated.note)
            assertEquals(null, updated.imageUri)
        }
    }

    @Test
    fun selectedLedgerPhotoCanBePreviewedAndRemovedBeforeSaving() {
        val file = File(baseContext.cacheDir, "selected-ledger-photo-" + UUID.randomUUID() + ".png")
        photos += file
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff333333.toInt())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var removed = false
        compose.setContent {
            MiloagentTheme {
                Box(Modifier.size(360.dp, 480.dp)) {
                    LedgerScreen(
                        transactions = emptyList(), amountText = "32", noteText = "午饭",
                        selectedType = TransactionType.EXPENSE, isListening = false,
                        hasVoiceDraft = false, isSaving = false, saveStatus = "",
                        imageUri = Uri.fromFile(file).toString(),
                        onAmountChange = {}, onNoteChange = {}, onTypeChange = {},
                        onVoiceClick = {}, onImageClick = {}, onImageRemove = { removed = true },
                        onQuickCapture = { _, _ -> }, onSubmit = {},
                    )
                }
            }
        }
        compose.onNodeWithText("移除照片").performScrollTo().assertIsDisplayed()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("待保存的账目照片")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("移除照片").performClick()
        compose.runOnIdle { assertTrue(removed) }
    }

    @Test
    fun journalPhotosCanPageAndExistingJournalCanBeEditedWithoutDuplication() {
        val uris = (1..5).map { index ->
            val file = File(baseContext.cacheDir, "life-photo-" + UUID.randomUUID() + ".png")
            photos += file
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xff000000.toInt() or (index * 0x202020))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            Uri.fromFile(file).toString()
        }
        val journal = requireNotNull(store.addJournal("Photo test", "A small memory", uris))
        compose.setContent {
            MiloagentTheme {
                Box(Modifier.size(360.dp, 480.dp)) { JournalRoute(store) }
            }
        }
        assertTrue(compose.onAllNodes(hasText("Photo test")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("查看历史").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Photo test"))
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("随笔照片 4")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("随笔照片 4").performScrollTo().performClick()
        compose.onNodeWithText("4 / 5").assertIsDisplayed()
        compose.onNodeWithContentDescription("照片 4").performTouchInput { swipeLeft() }
        compose.onNodeWithText("5 / 5").assertIsDisplayed()
        compose.onNodeWithContentDescription("照片 5").performTouchInput { swipeRight() }
        compose.onNodeWithText("4 / 5").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("编辑").performScrollTo().performClick()
        compose.onNodeWithText("修改随笔").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("Updated memory")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("移除最后一张照片"))
        compose.onNodeWithText("移除最后一张照片").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("保存修改"))
        compose.onNodeWithText("保存修改").performClick()
        compose.onNodeWithText("Updated memory").assertIsDisplayed()
        compose.runOnIdle {
            val restored = LifeStore(context).journals.value.single()
            assertEquals(journal.id, restored.id)
            assertEquals("Updated memory", restored.title)
            assertEquals(uris.take(4), restored.imageUris)
        }
    }

    @Test
    fun journalComposerAndSocialHistoryAreSeparatePages() {
        store.addJournal("留给自己的标题", "今天的正文留在时间流里。", emptyList())
        compose.setContent {
            MiloagentTheme {
                Box(Modifier.size(360.dp, 640.dp)) { JournalRoute(store) }
            }
        }
        assertTrue(compose.onAllNodes(hasText("留给自己的标题")).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasText("今天的正文留在时间流里。")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("查看历史").performClick()
        compose.onNodeWithText("留给自己的标题").assertIsDisplayed()
        compose.onNodeWithText("今天的正文留在时间流里。").assertIsDisplayed()
        compose.onNodeWithText("写一篇").performClick()
        assertTrue(compose.onAllNodes(hasText("留给自己的标题")).fetchSemanticsNodes().isEmpty())
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("新的一天")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("今天去散步，风很舒服。")
        compose.onNodeWithText("保存随笔").performScrollTo().performClick()
        compose.onNodeWithText("今天去散步，风很舒服。").assertIsDisplayed()
        compose.onNodeWithText("新的一天").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, LifeStore(context).journals.value.size)
        }
    }

    @Test
    fun scheduleRejectsInvalidTimingAndCanSaveAndEditAfterScrolling() {
        compose.setContent {
            MiloagentTheme {
                Box(Modifier.size(360.dp, 480.dp)) { ScheduleRoute(store) }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("Schedule test")
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextReplacement("2026-02-30")
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextReplacement("25:00")
        compose.onNodeWithText("加入日程").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(store.schedules.value.isEmpty()) }
        compose.onNodeWithText("日期或时间格式不正确，请使用 yyyy-MM-dd 和 HH:mm").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextReplacement("2026-11-07")
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextReplacement("10:30")
        compose.onNodeWithText("加入日程").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, store.schedules.value.size) }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("编辑"))
        compose.onNodeWithText("编辑").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("Edited schedule")
        compose.onNodeWithText("保存修改").performScrollTo().performClick()
        compose.runOnIdle {
            val restored = LifeStore(context).schedules.value.single()
            assertEquals("Edited schedule", restored.title)
            assertEquals("2026-11-07", restored.date)
            assertEquals("10:30", restored.time)
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("删除"))
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithText("删除日程").performClick()
        compose.runOnIdle {
            assertTrue(store.schedules.value.isEmpty())
            assertTrue(LifeStore(context).schedules.value.isEmpty())
        }
    }

    @Test
    fun typedMiloCaptureRequiresConfirmationAndKeepsTextSource() {
        val repository = TestLedgerRepository()
        val uiContext = isolatedUiContext()
        compose.setContent {
            MiloagentTheme {
                CompositionLocalProvider(LocalContext provides uiContext) {
                    Box(Modifier.size(360.dp, 480.dp)) {
                        MiloCaptureRoute(repository, store, onClose = {})
                    }
                }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("午饭 32 元")
        compose.runOnIdle { assertTrue(repository.records.value.isEmpty()) }
        compose.onNodeWithText("整理成待确认草稿").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("确认并整理"))
        compose.onNodeWithText("确认并整理").performClick()
        compose.waitUntil { repository.records.value.size == 1 }
        compose.runOnIdle {
            assertEquals(3200L, repository.records.value.single().amountCents)
            assertEquals(CaptureSource.TEXT_AI, repository.records.value.single().source)
        }
    }

    @Test
    fun typedMiloReminderStoresResolvedDateOnlyAfterConfirmation() {
        val repository = TestLedgerRepository()
        val uiContext = isolatedUiContext()
        compose.setContent {
            MiloagentTheme {
                CompositionLocalProvider(LocalContext provides uiContext) {
                    Box(Modifier.size(360.dp, 480.dp)) { MiloCaptureRoute(repository, store, onClose = {}) }
                }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("提醒我明天下午3点买花")
        compose.runOnIdle { assertTrue(store.schedules.value.isEmpty()) }
        compose.onNodeWithText("整理成待确认草稿").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("确认并整理"))
        compose.onNodeWithText("确认并整理").performClick()
        compose.runOnIdle {
            val event = store.schedules.value.single()
            assertEquals("买花", event.title)
            assertEquals(java.time.LocalDate.now().plusDays(1).toString(), event.date)
            assertEquals("15:00", event.time)
        }
    }

    @Test
    fun typedMiloJournalRequiresConfirmationAndCanBeReadBack() {
        val repository = TestLedgerRepository()
        val uiContext = isolatedUiContext()
        compose.setContent {
            MiloagentTheme {
                CompositionLocalProvider(LocalContext provides uiContext) {
                    Box(Modifier.size(360.dp, 480.dp)) { MiloCaptureRoute(repository, store, onClose = {}) }
                }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("随笔：今天心情很好")
        compose.runOnIdle { assertTrue(store.journals.value.isEmpty()) }
        compose.onNodeWithText("整理成待确认草稿").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("确认并整理"))
        compose.onNodeWithText("确认并整理").performClick()
        compose.runOnIdle {
            assertEquals("随笔：今天心情很好", LifeStore(context).journals.value.single().content)
        }
    }

    @Test
    fun settingsFieldsScrollAndSaveToIsolatedPreferences() {
        val uiContext = isolatedUiContext()
        compose.setContent {
            MiloagentTheme {
                CompositionLocalProvider(LocalContext provides uiContext) {
                    Box(Modifier.size(360.dp, 480.dp)) { SettingsRoute(onBack = {}) }
                }
            }
        }
        compose.onNodeWithText("Milo 模型接入").performClick()
        compose.onNodeWithContentDescription("添加模型配置").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("Test model")
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextReplacement("https://example.com/v1")
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextReplacement("test-model")
        compose.onAllNodes(hasSetTextAction())[3].performScrollTo().performTextReplacement("isolated-placeholder-key")
        compose.onNodeWithText("保存并启用").performScrollTo().assertIsDisplayed().performClick()

        compose.onNodeWithContentDescription("添加模型配置").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextReplacement("Second model")
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextReplacement("https://example.org/v1")
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextReplacement("second-model")
        compose.onAllNodes(hasSetTextAction())[3].performScrollTo().performTextReplacement("second-placeholder-key")
        compose.onNodeWithText("保存并启用").performScrollTo().performClick()
        compose.onNodeWithText("Test model").performScrollTo().performClick()
        compose.runOnIdle {
            val prefs = uiContext.getSharedPreferences("milo_settings", Context.MODE_PRIVATE)
            val profiles = JSONArray(prefs.getString("model_profiles", "[]"))
            assertEquals(2, profiles.length())
            val activeId = prefs.getString("active_model_profile", null)
            val activeProfile = (0 until profiles.length())
                .map { profiles.getJSONObject(it) }
                .first { it.getString("id") == activeId }
            assertEquals("test-model", activeProfile.getString("model"))
            assertEquals("https://example.com/v1", prefs.getString("base_url", null))
        }
    }

    @Test
    fun legacySingleModelSettingsMigrateToAnActiveProfile() {
        val uiContext = isolatedUiContext()
        val prefs = uiContext.getSharedPreferences("milo_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("provider", "DeepSeek")
            .putString("base_url", "https://api.deepseek.com/v1")
            .putString("model", "deepseek-chat")
            .putString("api_key", "legacy-placeholder-key")
            .commit()

        val profile = MiloModelProfileStore(uiContext).activeProfile()

        assertEquals("DeepSeek", profile?.name)
        assertEquals("deepseek-chat", profile?.model)
        assertEquals("legacy", prefs.getString("active_model_profile", null))
        assertTrue(prefs.getString("model_profiles", null)?.contains("legacy-placeholder-key") == true)
    }

    private fun isolatedUiContext(): Context = object : ContextWrapper(compose.activity) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences(preferenceName, mode)
    }

    private class TestLedgerRepository : LedgerRepository {
        val records = MutableStateFlow<List<Transaction>>(emptyList())
        override fun observeAll(): Flow<List<Transaction>> = records
        override suspend fun findById(id: String): Transaction? = records.value.find { it.id == id }
        override suspend fun add(transaction: Transaction) { records.value = listOf(transaction) + records.value }
        override suspend fun update(transaction: Transaction) {
            check(records.value.any { it.id == transaction.id })
            records.value = records.value.map { if (it.id == transaction.id) transaction else it }
        }
    }
}
