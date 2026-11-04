package com.malaxg.milo.feature.ledger

import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.core.domain.model.TransactionType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

internal data class LedgerMonthTotals(
    val expenseCents: Long,
    val incomeCents: Long,
)

internal fun summarizeMonth(
    transactions: List<Transaction>,
    month: YearMonth,
    zoneId: ZoneId,
): LedgerMonthTotals {
    val monthlyTransactions = transactions.filter { transaction ->
        YearMonth.from(Instant.ofEpochMilli(transaction.occurredAt).atZone(zoneId)) == month
    }
    return LedgerMonthTotals(
        expenseCents = monthlyTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents },
        incomeCents = monthlyTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
    )
}
