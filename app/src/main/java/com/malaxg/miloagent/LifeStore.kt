package com.malaxg.miloagent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class JournalRecord(
    val id: String,
    val title: String,
    val content: String,
    val imageUris: List<String>,
    val createdAt: Long,
    val favorite: Boolean = false,
)

data class ScheduleRecord(
    val id: String,
    val title: String,
    val date: String,
    val time: String,
    val createdAt: Long,
)

data class HomeConversationMessage(val text: String, val fromUser: Boolean)
data class HomeConversationSession(
    val id: String,
    val title: String,
    val messages: List<HomeConversationMessage> = emptyList(),
    val summary: String = "",
    val summarizedMessageCount: Int = 0,
    val pendingDraft: MiloDraft? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
data class HomeConversationsState(val sessions: List<HomeConversationSession>, val activeSessionId: String) {
    val activeSession: HomeConversationSession
        get() = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.first()
}

/** Local-only storage for life records that do not belong in the ledger table. */
class LifeStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _journals = MutableStateFlow(loadJournals())
    private val _schedules = MutableStateFlow(loadSchedules())

    val journals: StateFlow<List<JournalRecord>> = _journals.asStateFlow()
    val schedules: StateFlow<List<ScheduleRecord>> = _schedules.asStateFlow()

    fun loadHomeConversations(): HomeConversationsState = runCatching {
        val storedValue = preferences.getString(HOME_CONVERSATION_KEY, null)
        if (storedValue.isNullOrBlank()) {
            val fresh = HomeConversationSession(UUID.randomUUID().toString(), "新对话")
            return HomeConversationsState(listOf(fresh), fresh.id)
        }
        val json = JSONObject(storedValue)
        val storedSessions = json.optJSONArray("sessions")
        if (storedSessions != null) {
            val sessions = buildList {
                for (index in 0 until storedSessions.length()) {
                    storedSessions.optJSONObject(index)?.let { add(it.toHomeSession()) }
                }
            }.ifEmpty { listOf(HomeConversationSession(UUID.randomUUID().toString(), "新对话")) }
            HomeConversationsState(
                sessions = sessions,
                activeSessionId = json.optString("activeSessionId").takeIf { id -> sessions.any { it.id == id } }
                    ?: sessions.first().id,
            )
        } else {
            val legacy = json.toHomeSession(id = UUID.randomUUID().toString(), title = "历史对话")
            HomeConversationsState(listOf(legacy), legacy.id)
        }
    }.getOrElse {
        val session = HomeConversationSession(UUID.randomUUID().toString(), "新对话")
        HomeConversationsState(listOf(session), session.id)
    }

    fun saveHomeConversations(sessions: List<HomeConversationSession>, activeSessionId: String) {
        val json = JSONObject().apply {
            put("activeSessionId", activeSessionId)
            put("sessions", JSONArray().apply { sessions.forEach { put(it.toJson()) } })
        }
        preferences.edit().putString(HOME_CONVERSATION_KEY, json.toString()).apply()
    }

    private fun JSONObject.toHomeSession(
        id: String = optString("id").ifBlank { UUID.randomUUID().toString() },
        title: String = optString("title").ifBlank { "历史对话" },
    ): HomeConversationSession {
        val messagesJson = optJSONArray("messages") ?: JSONArray()
        val messages = buildList {
            for (index in 0 until messagesJson.length()) {
                messagesJson.optJSONObject(index)?.let { add(HomeConversationMessage(it.optString("text"), it.optBoolean("fromUser"))) }
            }
        }
        val draft = optJSONObject("pendingDraft")?.toMiloDraft()
        return HomeConversationSession(
            id = id,
            title = title,
            messages = messages,
            summary = optString("summary"),
            summarizedMessageCount = optInt("summarizedMessageCount").coerceIn(0, messages.size),
            pendingDraft = draft,
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun HomeConversationSession.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("summary", summary)
        put("summarizedMessageCount", summarizedMessageCount); put("updatedAt", updatedAt)
        put("messages", JSONArray().apply {
            messages.forEach { message -> put(JSONObject().put("text", message.text).put("fromUser", message.fromUser)) }
        })
        put("pendingDraft", pendingDraft?.toJson())
    }

    private fun MiloDraft.toJson(): JSONObject = JSONObject().apply {
        put("rawText", rawText); put("intent", intent.name); put("title", title)
        put("content", content); put("amount", amount); put("date", date); put("time", time)
        put("category", category); put("source", source.name)
    }

    private fun JSONObject.toMiloDraft(): MiloDraft = MiloDraft(
        rawText = optString("rawText"),
        intent = runCatching { CaptureIntent.valueOf(optString("intent")) }.getOrDefault(CaptureIntent.CHAT),
        title = optString("title"), content = optString("content"), amount = optString("amount"),
        date = optString("date"), time = optString("time"), category = optString("category"),
        source = runCatching { com.malaxg.milo.core.domain.model.CaptureSource.valueOf(optString("source")) }
            .getOrDefault(com.malaxg.milo.core.domain.model.CaptureSource.TEXT_AI),
    )

    fun addJournal(
        title: String,
        content: String,
        imageUris: List<String>,
        favorite: Boolean = false,
    ): JournalRecord? {
        if (title.isBlank() && content.isBlank() && imageUris.isEmpty()) return null
        val record = JournalRecord(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            content = content.trim(),
            imageUris = imageUris.toList(),
            createdAt = System.currentTimeMillis(),
            favorite = favorite,
        )
        val updated = listOf(record) + _journals.value
        _journals.value = updated
        persistJournals(updated)
        return record
    }

    fun toggleJournalFavorite(id: String): Boolean {
        val index = _journals.value.indexOfFirst { it.id == id }
        if (index < 0) return false
        val updated = _journals.value.toMutableList().apply {
            this[index] = this[index].copy(favorite = !this[index].favorite)
        }
        _journals.value = updated
        persistJournals(updated)
        return true
    }

    fun updateJournal(
        id: String,
        title: String,
        content: String,
        imageUris: List<String>,
        favorite: Boolean,
    ): JournalRecord? {
        if (title.isBlank() && content.isBlank() && imageUris.isEmpty()) return null
        val index = _journals.value.indexOfFirst { it.id == id }
        if (index < 0) return null
        val updatedRecord = _journals.value[index].copy(
            title = title.trim(),
            content = content.trim(),
            imageUris = imageUris.toList(),
            favorite = favorite,
        )
        val updated = _journals.value.toMutableList().apply { this[index] = updatedRecord }
        _journals.value = updated
        persistJournals(updated)
        return updatedRecord
    }

    fun addSchedule(title: String, date: String, time: String): ScheduleRecord? {
        if (title.isBlank()) return null
        val timing = normalizeScheduleTiming(date, time) ?: return null
        val record = ScheduleRecord(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            date = timing.first,
            time = timing.second,
            createdAt = System.currentTimeMillis(),
        )
        val updated = listOf(record) + _schedules.value
        _schedules.value = updated
        persistSchedules(updated)
        return record
    }

    fun updateSchedule(id: String, title: String, date: String, time: String): ScheduleRecord? {
        if (title.isBlank()) return null
        val timing = normalizeScheduleTiming(date, time) ?: return null
        val index = _schedules.value.indexOfFirst { it.id == id }
        if (index < 0) return null
        val updatedRecord = _schedules.value[index].copy(
            title = title.trim(),
            date = timing.first,
            time = timing.second,
        )
        val updated = _schedules.value.toMutableList().apply { this[index] = updatedRecord }
        _schedules.value = updated
        persistSchedules(updated)
        return updatedRecord
    }

    fun deleteSchedule(id: String): Boolean {
        val updated = _schedules.value.filterNot { it.id == id }
        if (updated.size == _schedules.value.size) return false
        _schedules.value = updated
        persistSchedules(updated)
        return true
    }

    private fun loadJournals(): List<JournalRecord> = runCatching {
        val array = JSONArray(preferences.getString(JOURNALS_KEY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val images = item.optJSONArray("images") ?: JSONArray()
                add(
                    JournalRecord(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        content = item.optString("content"),
                        imageUris = buildList {
                            for (imageIndex in 0 until images.length()) add(images.optString(imageIndex))
                        },
                        createdAt = item.optLong("createdAt"),
                        favorite = item.optBoolean("favorite", false),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun loadSchedules(): List<ScheduleRecord> = runCatching {
        val array = JSONArray(preferences.getString(SCHEDULES_KEY, "[]"))
        val records = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    ScheduleRecord(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        date = item.optString("date"),
                        time = item.optString("time"),
                        createdAt = item.optLong("createdAt"),
                    ),
                )
            }
        }
        val normalized = records.map { record ->
            if (record.date.isBlank() || record.createdAt <= 0L) return@map record
            val timing = normalizeScheduleTiming(record.date, record.time, record.createdAt)
                ?: return@map record
            record.copy(date = timing.first, time = timing.second)
        }
        if (normalized != records) persistSchedules(normalized)
        normalized
    }.getOrDefault(emptyList())

    private fun persistJournals(records: List<JournalRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("id", record.id)
                    put("title", record.title)
                    put("content", record.content)
                    put("createdAt", record.createdAt)
                    put("favorite", record.favorite)
                    put("images", JSONArray(record.imageUris))
                },
            )
        }
        preferences.edit().putString(JOURNALS_KEY, array.toString()).apply()
    }

    private fun persistSchedules(records: List<ScheduleRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("id", record.id)
                    put("title", record.title)
                    put("date", record.date)
                    put("time", record.time)
                    put("createdAt", record.createdAt)
                },
            )
        }
        preferences.edit().putString(SCHEDULES_KEY, array.toString()).apply()
    }

    private companion object {
        const val PREFERENCES = "milo_life_records"
        const val JOURNALS_KEY = "journals"
        const val SCHEDULES_KEY = "schedules"
        const val HOME_CONVERSATION_KEY = "home_conversation"
    }
}
