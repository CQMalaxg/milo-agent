package com.malaxg.milo.core.domain.model

/**
 * 记录来源。保留它，是为了日后复盘 AI 抽取准确率。
 */
enum class CaptureSource {
    MANUAL,
    TEXT_AI,
    VOICE,
    IMAGE,
}

enum class TransactionType {
    EXPENSE,
    INCOME,
}

enum class Mood {
    GREAT,
    GOOD,
    NEUTRAL,
    BAD,
    AWFUL,
}

/**
 * 记账记录。
 *
 * 时间统一使用 epoch millis（UTC），展示层才转本地时区——
 * 这样 domain 层保持纯 Kotlin，不依赖 java.time 或 kotlinx-datetime。
 */
data class Transaction(
    val id: String,
    val amountCents: Long,
    val currency: String = "CNY",
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryId: String? = null,
    val note: String? = null,
    val occurredAt: Long,
    val createdAt: Long,
    val source: CaptureSource,
    val imageUri: String? = null,
)

data class JournalEntry(
    val id: String,
    val content: String,
    val mood: Mood? = null,
    val occurredAt: Long,
    val createdAt: Long,
)

data class Note(
    val id: String,
    val title: String? = null,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)
