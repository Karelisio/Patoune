package com.karelisio.patoune.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

object Dates {
    private val fr = Locale.FRENCH
    val day: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", fr)
    val dayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", fr)
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", fr)
    val dayTime: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", fr)

    fun zone(): ZoneId = ZoneId.systemDefault()
    fun today(): LocalDate = LocalDate.now(zone())
    fun parse(s: String?): LocalDate? = s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun fmtDay(s: String?): String = parse(s)?.format(day) ?: "—"
    fun fmtDayTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone()).format(dayTime)
    fun fmtTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone()).format(time)
    fun fmtDay(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone()).format(day)
    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone()).toInstant().toEpochMilli()
    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()
}
