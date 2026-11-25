package com.karelisio.patoune.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** Formats de date dans la locale courante du téléphone (recalculés si la langue change). */
object Dates {
    private class Fmt(private val pattern: String) {
        @Volatile private var cache: Pair<Locale, DateTimeFormatter>? = null
        fun get(): DateTimeFormatter {
            val locale = Locale.getDefault()
            cache?.let { (l, f) -> if (l == locale) return f }
            return DateTimeFormatter.ofPattern(pattern, locale).also { cache = locale to it }
        }
    }
    private val dayFmt = Fmt("d MMM yyyy")
    private val dayShortFmt = Fmt("d MMM")
    private val timeFmt = Fmt("HH:mm")
    private val dayTimeFmt = Fmt("EEE d MMM, HH:mm")
    private val monthFmt = Fmt("MMM yyyy")

    val day: DateTimeFormatter get() = dayFmt.get()
    val dayShort: DateTimeFormatter get() = dayShortFmt.get()
    val time: DateTimeFormatter get() = timeFmt.get()
    val dayTime: DateTimeFormatter get() = dayTimeFmt.get()
    val month: DateTimeFormatter get() = monthFmt.get()

    fun zone(): ZoneId = ZoneId.systemDefault()
    fun today(): LocalDate = LocalDate.now(zone())
    fun parse(s: String?): LocalDate? = s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun fmtDay(s: String?): String = parse(s)?.format(day) ?: "—"
    fun fmtDayTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone()).format(dayTime)
    fun fmtTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone()).format(time)
    fun fmtDay(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone()).format(day)
    fun fmtMonth(m: YearMonth): String = m.format(month)
    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone()).toInstant().toEpochMilli()
    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()
}
