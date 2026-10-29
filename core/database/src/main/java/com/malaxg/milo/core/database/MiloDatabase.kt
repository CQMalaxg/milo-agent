package com.malaxg.milo.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.malaxg.milo.core.database.dao.TransactionDao
import com.malaxg.milo.core.database.entity.TransactionEntity

@Database(
    entities = [TransactionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MiloDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
}
