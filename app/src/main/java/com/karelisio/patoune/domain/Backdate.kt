package com.karelisio.patoune.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Saisie après coup (« j'ai oublié de noter la prise de ce matin ») : une entrée datée de plus d'1 h
 * n'est plus « en direct » — pas de push au foyer, pas d'annulation de rappel, pas d'alerte de seuil.
 */
object Backdate {
    const val RECENT_MS = 60 * 60_000L
    /** Tolérance pour une heure saisie à la minute près (horloges légèrement décalées). */
    const val FUTURE_TOLERANCE_MS = 5 * 60_000L

    fun isRecent(at: Long, now: Long): Boolean = now - at <= RECENT_MS
    fun isFuture(at: Long, now: Long): Boolean = at > now + FUTURE_TOLERANCE_MS

    /** Change le jour en gardant l'heure locale (heure inexistante au passage à l'heure d'été : décalée par java.time). */
    fun withDate(ms: Long, date: LocalDate, zone: ZoneId): Long =
        ZonedDateTime.of(date, Instant.ofEpochMilli(ms).atZone(zone).toLocalTime(), zone).toInstant().toEpochMilli()

    /** Change l'heure locale en gardant le jour ; secondes remises à zéro. */
    fun withTime(ms: Long, time: LocalTime, zone: ZoneId): Long =
        ZonedDateTime.of(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate(), time.withSecond(0).withNano(0), zone).toInstant().toEpochMilli()
}
