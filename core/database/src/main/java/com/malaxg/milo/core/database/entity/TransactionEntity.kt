package com.malaxg.milo.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["occurredAt"])],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amountCents: Long,
    val currency: String,
    val type: String,
    val categoryId: String?,
    val note: String?,
    val occurredAt: Long,
    val createdAt: Long,
    val source: String,
    val imageUri: String? = null,
)
