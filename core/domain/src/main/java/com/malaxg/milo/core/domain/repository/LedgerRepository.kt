package com.malaxg.milo.core.domain.repository

import com.malaxg.milo.core.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface LedgerRepository {

    fun observeAll(): Flow<List<Transaction>>

    suspend fun findById(id: String): Transaction?

    suspend fun add(transaction: Transaction)
}
