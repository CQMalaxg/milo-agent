package com.malaxg.miloagent

import com.malaxg.milo.core.domain.model.CaptureSource
import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.core.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class HistoryCalendarTest {
    @Test
    fun monthGridStartsOnMondayAndSumsOnlyLocalDateExpenses() {
        val zone = ZoneId.of("Asia/Shanghai")
        val days = buildHistoryCalendar(
            month = YearMonth.of(2026, 11),
            zoneId = zone,
            transactions = listOf(
                transaction("first", 1_200, "2026-11-02T17:00:00Z"),
                transaction("second", 300, "2026-11-03T00:15:00Z"),
                transaction("income", 5_000, "2026-11-03T02:00:00Z", TransactionType.INCOME),
                transaction("outside", 9_900, "2026-12-01T00:00:00Z"),
            ),
        )

        assertEquals(42, days.size)
        assertNull(days[0])
        assertEquals(LocalDate.of(2026, 11, 1), days[6]?.date)
        assertEquals(1_500L, days.filterNotNull().first { it.date == LocalDate.of(2026, 11, 3) }.expenseCents)
        assertEquals(0L, days.filterNotNull().first { it.date == LocalDate.of(2026, 11, 4) }.expenseCents)
    }

    private fun transaction(
        id: String,
        amountCents: Long,
        occurredAt: String,
        type: TransactionType = TransactionType.EXPENSE,
    ) = Transaction(
        id = id,
        amountCents = amountCents,
        type = type,
        occurredAt = Instant.parse(occurredAt).toEpochMilli(),
        createdAt = Instant.parse(occurredAt).toEpochMilli(),
        source = CaptureSource.MANUAL,
    )
}
