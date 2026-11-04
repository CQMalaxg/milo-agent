package com.malaxg.miloagent

import com.malaxg.milo.core.domain.model.CaptureSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MiloCaptureParserTest {
    @Test
    fun parsesExpenseAndIncomeAsSeparateIntents() {
        val expense = parseLocalCapture("午饭 32.50 元")
        assertEquals(CaptureIntent.EXPENSE, expense.intent)
        assertEquals("32.50", expense.amount)
        assertEquals("午饭", expense.title)

        val income = parseLocalCapture("工资到账 8000 元")
        assertEquals(CaptureIntent.INCOME, income.intent)
        assertEquals("8000", income.amount)
        assertEquals("工资到账", income.title)
    }

    @Test
    fun leavesPurposeBlankWhenInputContainsOnlyAnAmount() {
        val draft = parseLocalCapture("32")

        assertEquals(CaptureIntent.EXPENSE, draft.intent)
        assertEquals("32", draft.amount)
        assertEquals("", draft.title)
    }

    @Test
    fun removesChineseAmountAndCapturePrefixFromPurpose() {
        val draft = parseLocalCapture("帮我记一笔午饭三十二元")

        assertEquals("32", draft.amount)
        assertEquals("午饭", draft.title)
    }

    @Test
    fun extractsScheduleDateTimeAndTitle() {
        val draft = parseLocalCapture("提醒我明天下午3点买花")
        assertEquals(CaptureIntent.SCHEDULE, draft.intent)
        assertEquals("明天", draft.date)
        assertEquals("15:00", draft.time)
        assertEquals("买花", draft.title)
    }

    @Test
    fun parsesTodayHalfHourReminderWithoutTreatingTimeAsMoney() {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.of(2026, 11, 2).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val draft = parseLocalCapture("今天下午3点半提醒我开会")
        val occurredAt = Instant.ofEpochMilli(requireNotNull(draft.occurredAtMillis(now))).atZone(zone)

        assertEquals(CaptureIntent.SCHEDULE, draft.intent)
        assertEquals("今天", draft.date)
        assertEquals("15:30", draft.time)
        assertEquals("", draft.amount)
        assertEquals("开会", draft.title)
        assertEquals(LocalDate.of(2026, 11, 2), occurredAt.toLocalDate())
        assertEquals(LocalTime.of(15, 30), occurredAt.toLocalTime())
    }

    @Test
    fun parsesNextWeekdayAndChineseAmount() {
        val reminder = parseLocalCapture("下周星期六提醒我买菜")
        val expense = parseLocalCapture("买菜三十二元")

        assertEquals(CaptureIntent.SCHEDULE, reminder.intent)
        assertEquals("下周星期六", reminder.date)
        assertEquals("买菜", reminder.title)
        assertEquals(CaptureIntent.EXPENSE, expense.intent)
        assertEquals("32", expense.amount)
    }

    @Test
    fun resolvesWeekendToUpcomingSaturdayWhenTodayIsSunday() {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.of(2026, 11, 1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val draft = parseLocalCapture("周末买花")
        val occurredAt = Instant.ofEpochMilli(requireNotNull(draft.occurredAtMillis(now))).atZone(zone)

        assertEquals(CaptureIntent.SCHEDULE, draft.intent)
        assertEquals("周末", draft.date)
        assertEquals("买花", draft.title)
        assertEquals(LocalDate.of(2026, 11, 7), occurredAt.toLocalDate())
    }

    @Test
    fun invalidEditableDateAndTimeCannotBecomeCurrentTimestamp() {
        val draft = parseLocalCapture("午饭 32 元")

        assertEquals(null, draft.copy(date = "2026-13-40").occurredAtMillis())
        assertEquals(null, draft.copy(time = "25:90").occurredAtMillis())
    }

    @Test
    fun keepsJournalAndTextSource() {
        val draft = parseLocalCapture("今天心情很好", CaptureSource.TEXT_AI)
        assertEquals(CaptureIntent.JOURNAL, draft.intent)
        assertEquals("今天心情很好", draft.content)
        assertEquals(CaptureSource.TEXT_AI, draft.source)
    }

    @Test
    fun changingIntentClearsFieldsThatDoNotApply() {
        val draft = MiloDraft(
            amount = "32",
            category = "餐饮",
            date = "明天",
            time = "15:00",
        ).switchIntent(CaptureIntent.JOURNAL)

        assertEquals("", draft.amount)
        assertEquals("", draft.category)
        assertEquals("", draft.date)
        assertEquals("", draft.time)
        assertFalse(draft.intent == CaptureIntent.EXPENSE)
    }

    @Test
    fun relativeTransactionDateUsesTheDeviceTimeZone() {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.of(2026, 11, 2).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val draft = parseLocalCapture("昨天午饭 32 元").copy(date = "昨天", time = "09:30")
        val occurredAt = Instant.ofEpochMilli(requireNotNull(draft.occurredAtMillis(now))).atZone(zone)

        assertEquals(LocalDate.of(2026, 11, 1), occurredAt.toLocalDate())
        assertEquals(LocalTime.of(9, 30), occurredAt.toLocalTime())
    }

    @Test
    fun acceptsUnpaddedIsoDatesInEditableDrafts() {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.of(2026, 11, 2).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val draft = parseLocalCapture("提醒我2026-1-3上午9点开会")
        val occurredAt = Instant.ofEpochMilli(requireNotNull(draft.occurredAtMillis(now))).atZone(zone)

        assertEquals(CaptureIntent.SCHEDULE, draft.intent)
        assertEquals(LocalDate.of(2026, 1, 3), occurredAt.toLocalDate())
        assertEquals(LocalTime.of(9, 0), occurredAt.toLocalTime())
    }

    @Test
    fun resolvesRelativeScheduleDateAtConfirmationAndKeepsMissingFieldsEmpty() {
        val now = LocalDate.of(2026, 11, 2).atTime(12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertEquals("2026-11-03" to "15:30", normalizeScheduleTiming("明天", "下午3点半", now))
        assertEquals("" to "", normalizeScheduleTiming("", "", now))
        assertEquals(null, normalizeScheduleTiming("2026-02-30", "10:00", now))
    }
}
