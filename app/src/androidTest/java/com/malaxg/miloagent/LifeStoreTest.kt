package com.malaxg.miloagent

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class LifeStoreTest {
    @Test
    fun legacyRelativeScheduleIsResolvedAgainstItsOriginalCreationDate() {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val preferenceName = "life_store_legacy_test_${UUID.randomUUID()}"
        val testContext = object : ContextWrapper(baseContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                baseContext.getSharedPreferences(preferenceName, mode)
        }
        val preferences = testContext.getSharedPreferences("milo_life_records", Context.MODE_PRIVATE)
        val createdAt = LocalDate.of(2026, 11, 2).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val legacy = JSONObject()
            .put("id", "legacy-schedule")
            .put("title", "买花")
            .put("date", "明天")
            .put("time", "下午3点")
            .put("createdAt", createdAt)
        try {
            preferences.edit().putString("schedules", JSONArray().put(legacy).toString()).commit()
            val restored = LifeStore(testContext).schedules.value.single()
            assertEquals("2026-11-03", restored.date)
            assertEquals("15:00", restored.time)
            assertEquals("2026-11-03", LifeStore(testContext).schedules.value.single().date)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun journalPhotosFavoritesAndSchedulesSurviveStoreRecreation() {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val preferenceName = "life_store_test_${UUID.randomUUID()}"
        val testContext = object : ContextWrapper(baseContext) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                baseContext.getSharedPreferences(preferenceName, mode)
        }

        try {
            val store = LifeStore(testContext)
            val journal = store.addJournal(
                title = "周末散步",
                content = "路边的花开了",
                imageUris = listOf("content://test/photo-1", "content://test/photo-2"),
                favorite = true,
            )
            val schedule = store.addSchedule("买花", "2026-11-07", "10:30")
            val relativeSchedule = store.addSchedule("散步", "明天", "下午3点")

            assertNotNull(journal)
            assertNotNull(schedule)
            assertEquals(LocalDate.now().plusDays(1).toString(), relativeSchedule?.date)
            assertEquals("15:00", relativeSchedule?.time)
            val restored = LifeStore(testContext)

            assertEquals(listOf(journal), restored.journals.value)
            assertEquals(listOf(relativeSchedule, schedule), restored.schedules.value)
            assertEquals(
                journal?.copy(title = "雨天散步", imageUris = listOf("content://test/photo-2")),
                restored.updateJournal(
                    requireNotNull(journal).id,
                    "雨天散步",
                    "路边的花开了",
                    listOf("content://test/photo-2"),
                    true,
                ),
            )
            assertEquals(null, restored.updateJournal(journal.id, "", "", emptyList(), false))
            assertEquals(true, restored.toggleJournalFavorite(requireNotNull(journal).id))
            assertFalse(restored.journals.value.single().favorite)
            assertEquals(
                schedule?.copy(title = "买菜", date = "2026-11-08", time = "11:00"),
                restored.updateSchedule(requireNotNull(schedule).id, "买菜", "2026-11-08", "11:00"),
            )
            assertEquals(true, restored.deleteSchedule(requireNotNull(relativeSchedule).id))
            assertEquals(false, restored.deleteSchedule(relativeSchedule.id))
            assertEquals(listOf(schedule?.copy(title = "买菜", date = "2026-11-08", time = "11:00")), restored.schedules.value)
            assertEquals(null, restored.addSchedule("无效日程", "2026-02-30", "10:30"))
            assertEquals(null, restored.updateSchedule(schedule.id, "买菜", "2026-11-08", "25:00"))
            val edited = LifeStore(testContext)
            assertEquals("雨天散步", edited.journals.value.single().title)
            assertEquals(listOf("content://test/photo-2"), edited.journals.value.single().imageUris)
            assertEquals("买菜", edited.schedules.value.first { it.id == schedule.id }.title)
            assertEquals(listOf(schedule.id), edited.schedules.value.map { it.id })
        } finally {
            baseContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
