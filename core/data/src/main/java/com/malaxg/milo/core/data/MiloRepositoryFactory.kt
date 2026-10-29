package com.malaxg.milo.core.data

import android.content.Context
import com.malaxg.milo.core.data.repository.LedgerRepositoryImpl
import com.malaxg.milo.core.database.MiloDatabaseFactory
import com.malaxg.milo.core.domain.repository.LedgerRepository

/**
 * 仓库组装入口。
 * app 只需认识 domain 的接口与本工厂，不必感知 Room 的存在。
 */
object MiloRepositoryFactory {

    fun createLedgerRepository(context: Context): LedgerRepository {
        val database = MiloDatabaseFactory.create(context)
        return LedgerRepositoryImpl(database.transactionDao())
    }
}
