package com.malaxg.milo.core.domain.usecase

import com.malaxg.milo.core.common.MoneyCents
import com.malaxg.milo.core.domain.model.CaptureSource
import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.core.domain.model.TransactionType
import com.malaxg.milo.core.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ObserveTransactionsUseCase(
    private val repository: LedgerRepository,
) {
    operator fun invoke(): Flow<List<Transaction>> = repository.observeAll()
}

class AddTransactionUseCase(
    private val repository: LedgerRepository,
) {
    suspend operator fun invoke(
        amount: MoneyCents,
        type: TransactionType,
        categoryId: String? = null,
        note: String? = null,
        occurredAt: Long = System.currentTimeMillis(),
        source: CaptureSource = CaptureSource.MANUAL,
        imageUri: String? = null,
    ): Transaction {
        val now = System.currentTimeMillis()
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            amountCents = amount.value,
            type = type,
            categoryId = categoryId,
            note = note?.takeIf { it.isNotBlank() },
            occurredAt = occurredAt,
            createdAt = now,
            source = source,
            imageUri = imageUri,
        )
        repository.add(transaction)
        return transaction
    }
}
