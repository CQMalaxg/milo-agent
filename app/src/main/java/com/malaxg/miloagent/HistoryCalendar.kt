package com.malaxg.miloagent

import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.core.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal data class HistoryCalendarDay(
    val date: LocalDate,
    val expenseCents: Long,
)

internal fun buildHistoryCalendar(
    month: YearMonth,
    transactions: List<Transaction>,
    zoneId: ZoneId,
): List<HistoryCalendarDay?> {
    val firstDate = month.atDay(1)
    val leadingBlanks = firstDate.dayOfWeek.value - 1
    val dayCount = month.lengthOfMonth()
    val cellCount = ((leadingBlanks + dayCount + 6) / 7) * 7
    val expensesByDate = transactions.asSequence()
        .filter { it.type == TransactionType.EXPENSE }
        .groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zoneId).toLocalDate() }
        .mapValues { (_, entries) -> entries.sumOf { it.amountCents } }

    return List(cellCount) { index ->
        val dayOfMonth = index - leadingBlanks + 1
        if (dayOfMonth !in 1..dayCount) {
            null
        } else {
            val date = month.atDay(dayOfMonth)
            HistoryCalendarDay(date, expensesByDate[date] ?: 0L)
        }
    }
}
