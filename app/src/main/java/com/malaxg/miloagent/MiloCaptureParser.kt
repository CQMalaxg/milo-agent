package com.malaxg.miloagent

import com.malaxg.milo.core.domain.model.CaptureSource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class CaptureIntent { EXPENSE, INCOME, JOURNAL, SCHEDULE, CHAT }

data class MiloDraft(
    val rawText: String = "",
    val intent: CaptureIntent = CaptureIntent.EXPENSE,
    val title: String = "",
    val content: String = "",
    val amount: String = "",
    val date: String = "",
    val time: String = "",
    val category: String = "",
    val source: CaptureSource = CaptureSource.VOICE,
)

private val datePattern = Regex(
    """(?:20\d{2}-\d{1,2}-\d{1,2}|(?:(?:20\d{2}|[零〇一二三四五六七八九]{4})年)?(?:\d{1,2}|[零〇一二两三四五六七八九十]{1,3})月(?:\d{1,2}|[零〇一二两三四五六七八九十]{1,3})日|(?:(?:下下周|下周|本周|这周|上周)(?:星期|礼拜|周)?|(?:下下|下|本|这|上)?(?:周|星期|礼拜))[一二三四五六日天]|(?:下下|下|本|这)?周末|大后天|后天|明天|昨天|今天)""",
)
private val isoDatePattern = Regex("(20\\d{2})-(\\d{1,2})-(\\d{1,2})")
private val weekdayPattern = Regex(
    """(?:(下下周|下周|本周|这周|上周)(?:星期|礼拜|周)?|(?:(下下|下|本|这|上)?(?:星期|礼拜|周)))([一二三四五六日天])""",
)
private val monthDayPattern = Regex(
    """(?:(20\d{2}|[零〇一二三四五六七八九]{4})年)?(\d{1,2}|[零〇一二两三四五六七八九十]{1,3})月(\d{1,2}|[零〇一二两三四五六七八九十]{1,3})日""",
)
private val timePattern = Regex(
    """(凌晨|清晨|早上|上午|中午|下午|傍晚|晚上)?\s*(\d{1,2}|[零〇一二两三四五六七八九十]{1,3})(?:[:：](\d{1,2})|(点|时)(半|\d{1,2}分?)?)""",
)
private val arabicAmountPattern = Regex(
    """(?<![\d.])(?:[¥￥]\s*)?(\d+(?:\.\d{1,2})?|\.\d{1,2})(?![\d.])\s*(?:元|块钱|块)?""",
)
private val chineseAmountPattern = Regex("""([零〇一二两三四五六七八九十百千万]+)(?:人民币|元钱|元|块钱|块)""")
private val edgePunctuationPattern = Regex("""^[，,。.!！?？：:、\s]+|[，,。.!！?？：:、\s]+$""")

fun parseLocalCapture(
    text: String,
    source: CaptureSource = CaptureSource.VOICE,
): MiloDraft {
    val input = text.trim()
    val dateMatch = datePattern.find(input)
    val timeMatch = timePattern.find(input)
    val withoutDateOrTime = (dateMatch?.let { input.replace(it.value, " ") } ?: input)
        .let { remaining -> timeMatch?.let { remaining.replace(it.value, " ") } ?: remaining }
        .trim()
    val hasCurrency = Regex("[¥￥]|人民币|元|块钱|块").containsMatchIn(withoutDateOrTime)
    val hasExpenseSignal = listOf(
        "记账", "记一笔", "记一下", "支出", "消费", "花了", "买了", "买", "付款", "支付",
        "早餐", "午饭", "午餐", "晚饭", "晚餐", "咖啡", "地铁", "公交", "打车", "房租", "水电",
    ).any(withoutDateOrTime::contains)
    val hasIncomeSignal = listOf(
        "收入", "工资", "到账", "退款", "奖金", "收款", "收钱", "报销", "红包", "赚了", "收到",
    ).any(withoutDateOrTime::contains)
    val standaloneNumber = withoutDateOrTime.matches(Regex("[¥￥]?\\s*\\d+(?:\\.\\d{1,2})?\\s*(?:元|块钱|块)?"))
    val amount = extractAmount(withoutDateOrTime, hasCurrency || hasExpenseSignal || hasIncomeSignal || standaloneNumber)
    val hasAmount = amount.isNotBlank()
    val hasScheduleCommand = listOf("提醒", "日程", "安排", "记得", "待办", "计划", "预约").any(input::contains)
    val hasJournalSignal = listOf("随笔", "日记", "心情").any(input::contains)
    val hasChatSignal = listOf("聊", "你好", "谢谢", "为什么", "怎么样", "怎么做", "天气", "谁是", "什么是")
        .any(input::contains)
    val hasFutureDate = dateMatch?.value?.let(::isFutureDatePhrase) == true
    val hasScheduleAction = listOf("开会", "会议", "面试", "见面", "跑步", "看医生").any(input::contains)

    val intent = when {
        hasScheduleCommand -> CaptureIntent.SCHEDULE
        hasJournalSignal -> CaptureIntent.JOURNAL
        hasChatSignal && !hasAmount && !hasExpenseSignal && !hasIncomeSignal -> CaptureIntent.CHAT
        hasIncomeSignal -> CaptureIntent.INCOME
        hasAmount -> CaptureIntent.EXPENSE
        dateMatch != null && hasScheduleAction -> CaptureIntent.SCHEDULE
        timeMatch != null && dateMatch?.value == "今天" && hasScheduleAction -> CaptureIntent.SCHEDULE
        hasFutureDate -> CaptureIntent.SCHEDULE
        hasExpenseSignal -> CaptureIntent.EXPENSE
        dateMatch?.value in setOf("今天", "昨天") -> CaptureIntent.JOURNAL
        else -> CaptureIntent.CHAT
    }

    val date = dateMatch?.value.orEmpty()
    val time = timeMatch?.let(::formatTime).orEmpty()
    var cleanedText = input
    if (date.isNotBlank()) cleanedText = cleanedText.replace(date, " ")
    if (timeMatch != null) cleanedText = cleanedText.replace(timeMatch.value, " ")
    cleanedText = cleanedText
        .replace(chineseAmountPattern, " ")
        .replace(arabicAmountPattern, " ")
        .replace(Regex("^(?:(?:请)?(?:帮我)?(?:记一笔|记账|记录|记一下|记下|记|支出|收入)\\s*)+"), " ")
        .replace(Regex("(?:花了|花费|消费|支出|买了|付款|支付)\\s*"), " ")
        .replace(edgePunctuationPattern, "")
        .replace(Regex("^(?:(?:请)?(?:提醒我|提醒|记得|帮我|安排|日程|计划|预约|待办)\\s*)+"), "")
        .replace(edgePunctuationPattern, "")
        .replace(Regex("\\s+"), " ")
        .trim()

    return MiloDraft(
        rawText = input,
        intent = intent,
        title = when (intent) {
            CaptureIntent.SCHEDULE -> cleanedText
            CaptureIntent.EXPENSE, CaptureIntent.INCOME -> cleanedText
            else -> ""
        },
        content = if (intent == CaptureIntent.JOURNAL || intent == CaptureIntent.CHAT) input else "",
        amount = amount,
        date = date,
        time = time,
        source = source,
    )
}

private fun extractAmount(text: String, allowUnmarked: Boolean): String {
    chineseAmountPattern.find(text)?.let { match ->
        val amount = chineseNumberToInt(match.groupValues[1]) ?: return@let
        return amount.toString()
    }
    val match = arabicAmountPattern.find(text) ?: return ""
    val hasCurrency = Regex("[¥￥]|人民币|元|块钱|块").containsMatchIn(match.value)
    return match.groupValues[1].takeIf { hasCurrency || allowUnmarked }.orEmpty()
}

private fun isFutureDatePhrase(value: String): Boolean =
    value in setOf("明天", "后天", "大后天", "下周末", "下下周末") ||
        value.startsWith("下") ||
        value.startsWith("周") || value.startsWith("星期") || value.startsWith("礼拜") ||
        value.startsWith("本周") || value.startsWith("这周")

private fun formatTime(match: MatchResult): String {
    val period = match.groupValues[1]
    var hour = parseNumber(match.groupValues[2]) ?: return ""
    val minuteText = match.groupValues[3].ifBlank { match.groupValues[5] }
    val minute = when (minuteText) {
        "半" -> 30
        "" -> 0
        else -> parseNumber(minuteText.removeSuffix("分")) ?: return ""
    }
    if (hour !in 0..23 || minute !in 0..59) return ""
    when (period) {
        "下午", "晚上", "傍晚" -> if (hour in 1..11) hour += 12
        "中午" -> if (hour in 1..10) hour += 12
        "上午", "凌晨", "清晨" -> if (hour == 12) hour = 0
    }
    return "%02d:%02d".format(hour, minute)
}

private fun parseNumber(value: String): Int? {
    value.toIntOrNull()?.let { return it }
    if (value.isBlank()) return null
    val digits = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
    if (value.all { it in digits }) return value.map { digits.getValue(it) }.joinToString("").toIntOrNull()

    var total = 0
    var section = 0
    var current = 0
    for (char in value) {
        val digit = digits[char]
        if (digit != null) {
            current = digit
            continue
        }
        when (char) {
            '十' -> {
                section += (if (current == 0) 1 else current) * 10
                current = 0
            }
            '百' -> {
                section += (if (current == 0) 1 else current) * 100
                current = 0
            }
            '千' -> {
                section += (if (current == 0) 1 else current) * 1_000
                current = 0
            }
            '万' -> {
                section += current
                total += (if (section == 0) 1 else section) * 10_000
                section = 0
                current = 0
            }
            else -> return null
        }
    }
    return total + section + current
}

private fun chineseNumberToInt(value: String): Int? = parseNumber(value)

fun MiloDraft.switchIntent(intent: CaptureIntent): MiloDraft = copy(
    intent = intent,
    title = "",
    content = "",
    amount = if (intent == CaptureIntent.EXPENSE || intent == CaptureIntent.INCOME) amount else "",
    category = if (intent == CaptureIntent.EXPENSE || intent == CaptureIntent.INCOME) category else "",
    date = if (intent == CaptureIntent.SCHEDULE) date else "",
    time = if (intent == CaptureIntent.SCHEDULE) time else "",
)

fun MiloDraft.occurredAtMillis(nowEpochMillis: Long = System.currentTimeMillis()): Long? {
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zone)
    val localDate = if (date.isBlank()) now.toLocalDate() else parseDate(date.trim(), now.toLocalDate()) ?: return null
    val localTime = if (time.isBlank()) now.toLocalTime() else parseTime(time.trim()) ?: return null
    return LocalDateTime.of(localDate, localTime).atZone(zone).toInstant().toEpochMilli()
}

/** Resolve relative schedule dates once, when the user confirms the event. */
internal fun normalizeScheduleTiming(
    date: String,
    time: String,
    nowEpochMillis: Long = System.currentTimeMillis(),
): Pair<String, String>? {
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val resolvedDate = if (date.isBlank()) "" else (parseDate(date.trim(), today) ?: return null).toString()
    val resolvedTime = if (time.isBlank()) "" else {
        val parsed = parseTime(time.trim()) ?: return null
        "%02d:%02d".format(parsed.hour, parsed.minute)
    }
    return resolvedDate to resolvedTime
}

internal fun isValidScheduleTiming(date: String, time: String): Boolean =
    normalizeScheduleTiming(date, time) != null

private fun parseDate(value: String, today: LocalDate): LocalDate? {
    if (value.isBlank()) return null
    runCatching { LocalDate.parse(value) }.getOrNull()?.let { return it }
    isoDatePattern.matchEntire(value)?.let { match ->
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }
    when (value) {
        "今天" -> return today
        "昨天" -> return today.minusDays(1)
        "明天" -> return today.plusDays(1)
        "后天" -> return today.plusDays(2)
        "大后天" -> return today.plusDays(3)
    }
    if (value.endsWith("周末")) {
        val weekOffset = when {
            value.startsWith("下下周") -> 2L
            value.startsWith("下周") -> 1L
            else -> 0L
        }
        val saturday = today.with(DayOfWeek.MONDAY).plusWeeks(weekOffset).plusDays(5)
        return if (weekOffset == 0L && saturday.isBefore(today)) saturday.plusWeeks(1) else saturday
    }
    weekdayPattern.find(value)?.let { match ->
        val prefix = match.groupValues[1].ifBlank { match.groupValues[2] }
        val weekday = when (match.groupValues[3]) {
            "一" -> DayOfWeek.MONDAY
            "二" -> DayOfWeek.TUESDAY
            "三" -> DayOfWeek.WEDNESDAY
            "四" -> DayOfWeek.THURSDAY
            "五" -> DayOfWeek.FRIDAY
            "六" -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }
        val weekStart = today.with(DayOfWeek.MONDAY)
        val base = when (prefix) {
            "下下周", "下下" -> weekStart.plusWeeks(2)
            "下周", "下" -> weekStart.plusWeeks(1)
            "上周", "上" -> weekStart.minusWeeks(1)
            "本周", "这周", "本", "这" -> weekStart
            else -> return today.plusDays(((weekday.value - today.dayOfWeek.value + 7) % 7).toLong())
        }
        return base.plusDays((weekday.value - 1).toLong())
    }
    monthDayPattern.matchEntire(value)?.let { match ->
        val year = match.groupValues[1].takeIf { it.isNotBlank() }?.let(::parseNumber) ?: today.year
        val month = parseNumber(match.groupValues[2]) ?: return null
        val day = parseNumber(match.groupValues[3]) ?: return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }
    return null
}

private fun parseTime(value: String): LocalTime? {
    runCatching { LocalTime.parse(value) }.getOrNull()?.let { return it }
    val match = timePattern.matchEntire(value) ?: return null
    val formatted = formatTime(match)
    if (formatted.isBlank()) return null
    return runCatching { LocalTime.parse(formatted) }.getOrNull()
}
