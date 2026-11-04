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
            if (!trimmed.matches(Regex("-?(?:\\d+(?:\\.\\d{0,2})?|\\.\\d{1,2})"))) return ZERO
            val negative = trimmed.startsWith('-')
            val unsigned = trimmed.removePrefix("-")
            val parts = unsigned.split('.', limit = 2)
            val yuanPart = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return ZERO
            if (yuanPart > Long.MAX_VALUE / 100) return ZERO
            val centPart = parts.getOrNull(1)?.padEnd(2, '0')?.toLongOrNull() ?: 0L
            val baseCents = yuanPart * 100
            if (baseCents > Long.MAX_VALUE - centPart) return ZERO
            val cents = baseCents + centPart
            return MoneyCents(if (negative) -cents else cents)
        }
    }
}

/** 格式化为「元」展示，例如 1234 -> 12.34 */
fun MoneyCents.toDisplayString(): String {
    val sign = if (value < 0) "-" else ""
    val yuan = kotlin.math.abs(value / 100)
    val cent = kotlin.math.abs(value % 100)
    return buildString {
        append(sign)
        append(yuan)
        append('.')
        append(cent.toString().padStart(2, '0'))
    }
}
