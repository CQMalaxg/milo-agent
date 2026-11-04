package com.malaxg.milo.feature.ledger

import com.malaxg.milo.core.domain.model.CaptureSource
import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.core.domain.model.TransactionType
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerSummaryTest {
    @Test
    fun greetingChangesAcrossTimesOfDay() {
        assertEquals("早安，愿今天从容开始", timeGreeting(8))
        assertEquals("午间好，记得好好吃饭", timeGreeting(12))
        assertEquals("下午好，慢慢把事情做好", timeGreeting(16))
        assertEquals("晚上好，今天也辛苦了", timeGreeting(20))
        assertEquals("夜深了，记得早点休息", timeGreeting(23))
    }

    @Test
    fun onlyCountsTransactionsInTheSelectedLocalMonth() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val transactions = listOf(
            transaction("october", "2026-10-31T23:59:00", zoneId, 3200, TransactionType.EXPENSE),
            transaction("november-expense", "2026-11-01T00:01:00", zoneId, 1800, TransactionType.EXPENSE),
            transaction("november-income", "2026-11-02T09:00:00", zoneId, 5000, TransactionType.INCOME),
        )

        val totals = summarizeMonth(transactions, YearMonth.of(2026, 11), zoneId)

        assertEquals(1800L, totals.expenseCents)
        assertEquals(5000L, totals.incomeCents)
    }

    private fun transaction(
        id: String,
        localDateTime: String,
        zoneId: ZoneId,
        amountCents: Long,
        type: TransactionType,
    ) = Transaction(
        id = id,
        amountCents = amountCents,
        type = type,
        occurredAt = LocalDateTime.parse(localDateTime).atZone(zoneId).toInstant().toEpochMilli(),
        createdAt = 0L,
        source = CaptureSource.MANUAL,
    )
}
