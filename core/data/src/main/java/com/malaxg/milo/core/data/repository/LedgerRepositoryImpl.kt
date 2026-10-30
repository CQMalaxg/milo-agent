package com.malaxg.milo.core.data.repository

import com.malaxg.milo.core.database.dao.TransactionDao
import com.malaxg.milo.core.database.entity.TransactionEntity
import com.malaxg.milo.core.domain.model.CaptureSource
import com.malaxg.milo.core.domain.model.Transaction
import com.malaxg.milo.core.domain.model.TransactionType
import com.malaxg.milo.core.domain.repository.LedgerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LedgerRepositoryImpl(
    private val transactionDao: TransactionDao,
) : LedgerRepository {

    override fun observeAll(): Flow<List<Transaction>> =
        transactionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: String): Transaction? =
        transactionDao.findById(id)?.toDomain()

    override suspend fun add(transaction: Transaction) {
        transactionDao.insert(transaction.toEntity())
    }
}

private fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amountCents = amountCents,
    currency = currency,
    type = TransactionType.valueOf(type),
    categoryId = categoryId,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    source = runCatching { CaptureSource.valueOf(source) }.getOrDefault(CaptureSource.MANUAL),
    imageUri = imageUri,
)

private fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amountCents = amountCents,
    currency = currency,
    type = type.name,
    categoryId = categoryId,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    source = source.name,
    imageUri = imageUri,
)
