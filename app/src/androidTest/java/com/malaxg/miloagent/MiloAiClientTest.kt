package com.malaxg.miloagent

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MiloAiClientTest {
    @Test
    fun extractMapsEditableExpenseFieldsAndSendsOnlyCurrentInput() = withIsolatedPreferences { context, preferences ->
        configure(preferences)
        val connection = StubConnection(
            URL("https://example.test/v1/chat/completions"),
            200,
            completionResponse("""{"kind":"expense","title":"午饭","amountYuan":"32.50","category":"餐饮"}"""),
        )
        val client = MiloAiClient(context) { connection }

        val result = runBlocking { client.extract("午饭") }

        val draft = (result as? MiloAiResult.Capture)?.draft
        assertNotNull(draft)
        assertEquals(CaptureIntent.EXPENSE, draft?.intent)
        assertEquals("32.50", draft?.amount)
        assertEquals("午饭", draft?.title)
        assertEquals("餐饮", draft?.category)
        assertEquals("", draft?.date)
        assertEquals("", draft?.time)
        assertEquals("午饭", requestUserText(connection))
        assertEquals("Bearer test-only-key", connection.getRequestProperty("Authorization"))
    }

    @Test
    fun extractPreservesChatIntentInsteadOfCreatingAnAction() = withIsolatedPreferences { context, preferences ->
        configure(preferences)
        val connection = StubConnection(
            URL("https://example.test/v1/chat/completions"),
            200,
            completionResponse("""{"kind":"chat","content":"你好"}"""),
        )
        val client = MiloAiClient(context) { connection }

        val result = runBlocking { client.extract("你好") }

        val draft = (result as? MiloAiResult.Capture)?.draft
        assertNotNull(draft)
        assertEquals(CaptureIntent.CHAT, draft?.intent)
        assertEquals("你好", draft?.content)
    }

    @Test
    fun extractTreatsPlainTextCompletionAsAChatReply() = withIsolatedPreferences { context, preferences ->
        configure(preferences)
        val connection = StubConnection(
            URL("https://example.test/v1/chat/completions"),
            200,
            completionResponse("你好，我是 Milo。今天想聊什么？"),
        )
        val client = MiloAiClient(context) { connection }

        val result = runBlocking { client.extract("你好") }

        assertEquals(MiloAiResult.Reply("你好，我是 Milo。今天想聊什么？"), result)
    }

    @Test
    fun extractReturnsClarificationAsASeparateResult() = withIsolatedPreferences { context, preferences ->
        configure(preferences)
        val connection = StubConnection(
            URL("https://example.test/v1/chat/completions"),
            200,
            completionResponse("""{"kind":"clarification","question":"这笔花了多少钱？"}"""),
        )
        val client = MiloAiClient(context) { connection }

        val result = runBlocking { client.extract("帮我记午饭") }

        assertEquals(MiloAiResult.Clarification("这笔花了多少钱？"), result)
    }

    @Test
    fun chatReadsOpenAiCompatibleTextContentBlocks() = withIsolatedPreferences { context, preferences ->
        configure(preferences)
        val connection = StubConnection(
            URL("https://example.test/v1/chat/completions"),
            200,
            """{"choices":[{"message":{"content":[{"type":"text","text":"你好，今天过得怎么样？"}]}}]}""",
        )
        val client = MiloAiClient(context) { connection }

        val result = runBlocking {
            client.chat("你好", listOf(MiloChatTurn(MiloChatRole.ASSISTANT, "你好，我是 Milo。")))
        }

        assertEquals(MiloAiResult.Reply("你好，今天过得怎么样？"), result)
        val messages = JSONObject(connection.requestBody()).getJSONArray("messages")
        assertEquals("assistant", messages.getJSONObject(1).getString("role"))
        assertEquals("user", messages.getJSONObject(2).getString("role"))
    }

    @Test
    fun chatIncludesCompressedConversationSummaryInSystemContext() = withIsolatedPreferences { context, preferences ->
        configure(preferences)
        val connection = StubConnection(
            URL("https://example.test/v1/chat/completions"),
            200,
            completionResponse("记得，你平时周末喜欢散步。"),
        )
        val client = MiloAiClient(context) { connection }

        val result = runBlocking {
            client.chat("我周末有什么安排？", contextSummary = "用户周末喜欢散步。")
        }

        assertEquals(MiloAiResult.Reply("记得，你平时周末喜欢散步。"), result)
        val messages = JSONObject(connection.requestBody()).getJSONArray("messages")
        assertTrue(messages.getJSONObject(0).getString("content").contains("用户周末喜欢散步。"))
        assertEquals("我周末有什么安排？", messages.getJSONObject(1).getString("content"))
    }

    @Test
    fun summarizeConversationCombinesExistingSummaryAndOlderTurns() = withIsolatedPreferences { context, preferences ->
        configure(preferences)
        val connection = StubConnection(
            URL("https://example.test/v1/chat/completions"),
            200,
            completionResponse("用户常在周末散步，也计划本周末去公园。"),
        )
        val client = MiloAiClient(context) { connection }

        val summary = runBlocking {
            client.summarizeConversation(
                "用户常在周末散步。",
                listOf(MiloChatTurn(MiloChatRole.USER, "这个周末去公园吧。")),
            )
        }

        assertEquals("用户常在周末散步，也计划本周末去公园。", summary)
        val requestMessages = JSONObject(connection.requestBody()).getJSONArray("messages")
        assertTrue(requestMessages.getJSONObject(1).getString("content").contains("已有摘要"))
        assertTrue(requestMessages.getJSONObject(1).getString("content").contains("这个周末去公园吧。"))
    }

    @Test
    fun httpBaseUrlFailsBeforeOpeningAConnection() = withIsolatedPreferences { context, preferences ->
        preferences.edit()
            .putString("api_key", "test-only-key")
            .putString("base_url", "http://example.test/v1")
            .putString("model", "test-model")
            .commit()
        var openedConnections = 0
        val client = MiloAiClient(context) {
            openedConnections += 1
            error("An HTTP URL must not reach the connection factory")
        }

        val result = runBlocking { client.extract("午饭 32 元") }

        assertEquals(MiloAiResult.Failure("Base URL 必须使用 HTTPS"), result)
        assertEquals(0, openedConnections)
    }

    @Test
    fun nonSuccessResponseBecomesFailure() = withIsolatedPreferences { context, preferences ->
        configure(preferences)
        val connection = StubConnection(
            URL("https://example.test/v1/chat/completions"),
            401,
            "unauthorized",
        )
        val client = MiloAiClient(context) { connection }

        val result = runBlocking { client.chat("你好") }

        assertTrue(result is MiloAiResult.Failure)
    }

    private fun configure(preferences: SharedPreferences) {
        preferences.edit()
            .putString("api_key", "test-only-key")
            .putString("base_url", "https://example.test/v1")
            .putString("model", "test-model")
            .commit()
    }

    private fun completionResponse(content: String) =
        """{"choices":[{"message":{"content":${JSONObject.quote(content)}}}]}"""

    private fun requestUserText(connection: StubConnection): String =
        JSONObject(connection.requestBody()).getJSONArray("messages").getJSONObject(1).getString("content")

    private inline fun <T> withIsolatedPreferences(block: (Context, SharedPreferences) -> T): T {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val preferenceName = "milo_ai_client_test_${UUID.randomUUID()}"
        val context = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                baseContext.getSharedPreferences(preferenceName, mode)
        }
        val preferences = context.getSharedPreferences("milo_settings", Context.MODE_PRIVATE)
        return try {
            block(context, preferences)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    private class StubConnection(
        url: URL,
        private val status: Int,
        private val responseBody: String,
    ) : HttpURLConnection(url) {
        private val requestBytes = ByteArrayOutputStream()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getOutputStream(): OutputStream = requestBytes
        override fun getResponseCode(): Int = status
        override fun getInputStream(): InputStream = ByteArrayInputStream(responseBytes())
        override fun getErrorStream(): InputStream = ByteArrayInputStream(responseBytes())

        fun requestBody(): String = String(requestBytes.toByteArray(), StandardCharsets.UTF_8)

        private fun responseBytes() = responseBody.toByteArray(StandardCharsets.UTF_8)
    }
}
