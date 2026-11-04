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
data class HomeConversationState(val messages: List<HomeConversationMessage>, val pendingDraft: MiloDraft?)

/** Local-only storage for life records that do not belong in the ledger table. */
class LifeStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _journals = MutableStateFlow(loadJournals())
    private val _schedules = MutableStateFlow(loadSchedules())

    val journals: StateFlow<List<JournalRecord>> = _journals.asStateFlow()
    val schedules: StateFlow<List<ScheduleRecord>> = _schedules.asStateFlow()

    fun loadHomeConversation(): HomeConversationState = runCatching {
        val json = JSONObject(preferences.getString(HOME_CONVERSATION_KEY, "{}"))
        val messagesJson = json.optJSONArray("messages") ?: JSONArray()
        val messages = buildList {
            for (index in 0 until messagesJson.length()) {
                val item = messagesJson.getJSONObject(index)
                add(HomeConversationMessage(item.optString("text"), item.optBoolean("fromUser")))
            }
        }
        val draftJson = json.optJSONObject("pendingDraft")
        val draft = draftJson?.let {
            MiloDraft(
                rawText = it.optString("rawText"),
                intent = runCatching { CaptureIntent.valueOf(it.optString("intent")) }.getOrDefault(CaptureIntent.CHAT),
                title = it.optString("title"), content = it.optString("content"), amount = it.optString("amount"),
                date = it.optString("date"), time = it.optString("time"), category = it.optString("category"),
                source = runCatching { com.malaxg.milo.core.domain.model.CaptureSource.valueOf(it.optString("source")) }
                    .getOrDefault(com.malaxg.milo.core.domain.model.CaptureSource.TEXT_AI),
            )
        }
        HomeConversationState(messages, draft)
    }.getOrDefault(HomeConversationState(emptyList(), null))

    fun saveHomeConversation(messages: List<HomeConversationMessage>, draft: MiloDraft?) {
        val json = JSONObject().apply {
            put("messages", JSONArray().apply {
                messages.forEach { message -> put(JSONObject().put("text", message.text).put("fromUser", message.fromUser)) }
            })
            put("pendingDraft", draft?.let {
                JSONObject().apply {
                    put("rawText", it.rawText); put("intent", it.intent.name); put("title", it.title)
                    put("content", it.content); put("amount", it.amount); put("date", it.date); put("time", it.time)
                    put("category", it.category); put("source", it.source.name)
                }
            })
        }
        preferences.edit().putString(HOME_CONVERSATION_KEY, json.toString()).apply()
    }

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
