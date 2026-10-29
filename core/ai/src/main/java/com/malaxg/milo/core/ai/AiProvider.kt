package com.malaxg.milo.core.ai

/**
 * AI 能力抽象层。
 *
 * P0 只定义契约，不提供实现 —— P2 才会接入云端实现（BYOK）。
 * 之所以现在就把接口立好，是为了避免日后为了实现切换而重构业务代码。
 *
 * 约定：
 * 1. 业务层永远只依赖本接口，不感知底层是云端、端侧还是规则兜底；
 * 2. AI 的输出不允许直接落库，必须先经过确认卡片；
 * 3. 实现必须是可降级的（网络不可用 / 用户未配置 Key 时仍能工作）。
 */
interface AiProvider {

    /** 当前实现是否可用（例如用户尚未配置 API Key 时返回 false）。 */
    suspend fun isAvailable(): Boolean

    /**
     * 执行一次抽取/生成。
     * @return 结构化的抽取结果，供上层填充确认卡片。
     */
    suspend fun complete(request: CompletionRequest): CompletionResult
}

data class CompletionRequest(
    /** 用户的原始输入，例如「午饭 32，AA 下来我付了 18」。 */
    val rawInput: String,
    /** 注入当前时间上下文，避免模型算错「昨天」这类相对时间。 */
    val nowEpochMillis: Long,
    val timeZoneId: String,
)

sealed interface CompletionResult {

    /** 抽取成功，待用户确认。 */
    data class Success(val draft: RecordDraft) : CompletionResult

    /** 信息不足，模型主动反问——宁可反问也不要瞎猜落库。 */
    data class NeedClarification(val question: String) : CompletionResult

    /** 无法处理（未配置 Key / 网络失败等），上层应降级到手工录入。 */
    data class Unavailable(val reason: String) : CompletionResult
}

/** 待确认的记录草稿。确认前不落库。 */
data class RecordDraft(
    val kind: RecordKind,
    val amountCents: Long? = null,
    val currency: String = "CNY",
    val category: String? = null,
    val note: String? = null,
    val occurredAt: Long? = null,
    val content: String? = null,
)

enum class RecordKind {
    EXPENSE,
    INCOME,
    JOURNAL,
    NOTE,
}
