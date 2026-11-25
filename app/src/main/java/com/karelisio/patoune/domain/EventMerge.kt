package com.karelisio.patoune.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** Un changement de valeur d'un champ d'une ligne. Les suppressions sont le champ "deleted" = true. */
@Serializable
data class FieldEvent(
    val eventId: String,
    val entity: String,
    val rowId: String,
    val field: String,
    val value: JsonElement,
    val hlc: String,
    val deviceId: String,
) {
    val clock: Hlc get() = Hlc.decode(hlc)
}

object EventMerge {
    /** Ordre total : horloge, puis appareil, puis identifiant d'événement. */
    val order: Comparator<FieldEvent> = compareBy<FieldEvent>({ it.clock }, { it.deviceId }, { it.eventId })

    /** Vrai si [incoming] doit remplacer la valeur dont l'horloge gagnante est [currentHlc]/[currentDevice]. */
    fun wins(incoming: FieldEvent, currentHlc: String?, currentDevice: String?): Boolean {
        if (currentHlc == null) return true
        val c = Hlc.decode(currentHlc)
        val cmp = incoming.clock.compareTo(c)
        return cmp > 0 || (cmp == 0 && incoming.deviceId > (currentDevice ?: ""))
    }

    /** Diff champ par champ entre deux états d'une ligne (les champs [ignored] sont locaux). */
    fun diff(old: JsonObject?, new: JsonObject, ignored: Set<String> = emptySet()): Map<String, JsonElement> =
        new.filter { (k, v) -> k !in ignored && k != "id" && old?.get(k) != v }

    /** Réduit un ensemble d'événements à l'état final de chaque ligne, quel que soit l'ordre d'arrivée. */
    fun reduce(events: Collection<FieldEvent>): Map<Pair<String, String>, JsonObject> {
        val winners = HashMap<Triple<String, String, String>, FieldEvent>()
        for (e in events) {
            val key = Triple(e.entity, e.rowId, e.field)
            val cur = winners[key]
            if (cur == null || order.compare(e, cur) > 0) winners[key] = e
        }
        return winners.values.groupBy { it.entity to it.rowId }.mapValues { (k, evs) ->
            buildJsonObject {
                put("id", kotlinx.serialization.json.JsonPrimitive(k.second))
                evs.forEach { put(it.field, it.value) }
            }
        }
    }
}

/**
 * Magasin en mémoire qui applique les événements avec la même règle que la base Room.
 * Sert aux tests de convergence et de migration.
 */
class FieldStore {
    private val rows = HashMap<Pair<String, String>, MutableMap<String, JsonElement>>()
    private val clocks = HashMap<Triple<String, String, String>, FieldEvent>()
    val log = ArrayList<FieldEvent>()

    fun apply(e: FieldEvent): Boolean {
        val key = Triple(e.entity, e.rowId, e.field)
        val cur = clocks[key]
        if (cur != null && EventMerge.order.compare(e, cur) <= 0) return false
        clocks[key] = e
        rows.getOrPut(e.entity to e.rowId) { mutableMapOf("id" to kotlinx.serialization.json.JsonPrimitive(e.rowId)) }[e.field] = e.value
        if (log.none { it.eventId == e.eventId }) log += e
        return true
    }

    fun snapshot(): Map<Pair<String, String>, JsonObject> = rows.mapValues { JsonObject(it.value) }
}
