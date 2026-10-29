package com.malaxg.milo.core.common

/**
 * 金额一律以「分」为单位存储，全链路不出现 Double，避免浮点误差。
 */
@JvmInline
value class MoneyCents(val value: Long) {

    operator fun plus(other: MoneyCents): MoneyCents = MoneyCents(value + other.value)

    operator fun minus(other: MoneyCents): MoneyCents = MoneyCents(value - other.value)

    companion object {
        val ZERO: MoneyCents = MoneyCents(0L)

        /** 仅用于人工录入的兜底转换，AI / 解析链路应直接产出分。 */
        fun fromYuan(yuan: String): MoneyCents {
            val trimmed = yuan.trim()
            val negative = trimmed.startsWith("-")
            val abs = trimmed.trimStart('-')
            val parts = abs.split('.', limit = 2)
            val yuanPart = parts[0].ifEmpty { "0" }
            val centPart = parts.getOrNull(1)?.padEnd(2, '0')?.take(2) ?: "00"
            val cents = yuanPart.toLongOrNull()?.times(100)?.plus(centPart.toLongOrNull() ?: 0L) ?: 0L
            return MoneyCents(if (negative) -cents else cents)
        }
    }
}

/** 格式化为「元」展示，例如 1234 -> 12.34 */
fun MoneyCents.toDisplayString(): String {
    val sign = if (value < 0) "-" else ""
    val abs = if (value == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(value)
    val yuan = abs / 100
    val cent = abs % 100
    return buildString {
        append(sign)
        append(yuan)
        append('.')
        append(cent.toString().padStart(2, '0'))
    }
}
