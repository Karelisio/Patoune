package com.karelisio.patoune.data

import androidx.room.withTransaction
import com.karelisio.patoune.domain.EventMerge
import com.karelisio.patoune.domain.FieldEvent
import com.karelisio.patoune.domain.Hlc
import com.karelisio.patoune.domain.HlcClock
import com.karelisio.patoune.domain.newId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.reflect.KClass

/** Décrit comment une entité est lue, écrite et sérialisée pour la sync. */
class Adapter<T : Syncable>(
    val name: String,
    val type: KClass<T>,
    val serializer: KSerializer<T>,
    val dao: SyncDao<T>,
    /** Champs propres à l'appareil, jamais envoyés. */
    val localFields: Set<String> = emptySet(),
    /** Valeurs locales d'une ligne reçue pour la première fois. */
    val onRemoteInsert: (JsonObject) -> JsonObject = { it },
)

/**
 * Point d'entrée unique pour écrire des données : chaque écriture produit des événements de champ
 * horodatés (HLC) dans le journal local. En solo le journal reste local ; en partagé il est envoyé.
 */
class Store(
    private val db: PatouneDb,
    private val deviceId: String,
    private val author: suspend () -> String,
    private val onLocalChange: () -> Unit = {},
) {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = true }
    val clock = HlcClock(deviceId)

    val adapters: List<Adapter<out Syncable>> = listOf(
        Adapter("animal", Animal::class, Animal.serializer(), db.animals()),
        Adapter("medication", Medication::class, Medication.serializer(), db.medications()),
        Adapter("dose", DoseLog::class, DoseLog.serializer(), db.doses()),
        Adapter("breath", BreathCount::class, BreathCount.serializer(), db.breaths()),
        Adapter("crisis", Crisis::class, Crisis.serializer(), db.crises()),
        Adapter("media", MediaItem::class, MediaItem.serializer(), db.media(), setOf("localPath", "uploaded")) {
            JsonObject(it + ("uploaded" to JsonPrimitive(true)))
        },
        Adapter("observation", Observation::class, Observation.serializer(), db.observations()),
        Adapter("care", CareItem::class, CareItem.serializer(), db.care()),
        Adapter("visit", VetVisit::class, VetVisit.serializer(), db.visits()),
        Adapter("weight", WeightEntry::class, WeightEntry.serializer(), db.weights()),
        Adapter("document", HealthDocument::class, HealthDocument.serializer(), db.documents()),
        Adapter("member", Member::class, Member.serializer(), db.members()),
    )
    private val byName = adapters.associateBy { it.name }
    private val byType = adapters.associateBy { it.type }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Syncable> adapterFor(row: T): Adapter<T> = byType[row::class] as Adapter<T>

    private fun <T : Syncable> stamp(row: T, now: Long, createdBy: String, deleted: Boolean = row.deleted): T {
        val a = adapterFor(row)
        val obj = json.encodeToJsonElement(a.serializer, row).jsonObject
        val stamped = JsonObject(obj + mapOf(
            "updatedAt" to JsonPrimitive(now),
            "createdBy" to JsonPrimitive(createdBy),
            "deleted" to JsonPrimitive(deleted),
            "id" to JsonPrimitive(row.id.ifEmpty { newId() }),
        ))
        return json.decodeFromJsonElement(a.serializer, stamped)
    }

    /** Enregistre (création ou modification) et journalise les champs modifiés. Retourne la ligne écrite. */
    suspend fun <T : Syncable> save(row: T): T {
        val a = adapterFor(row)
        val me = author()
        val result = db.withTransaction {
            val old = if (row.id.isNotEmpty()) a.dao.byId(row.id) else null
            val stamped = stamp(row, System.currentTimeMillis(), old?.createdBy?.takeIf { it.isNotEmpty() } ?: row.createdBy.ifEmpty { me })
            val oldJson = old?.let { json.encodeToJsonElement(a.serializer, it).jsonObject }
            val newJson = json.encodeToJsonElement(a.serializer, stamped).jsonObject
            val changes = EventMerge.diff(oldJson, newJson, a.localFields)
            val events = changes.map { (field, value) ->
                val hlc = clock.tick()
                db.sync().putClock(FieldClock(a.name, stamped.id, field, hlc.encode(), deviceId))
                SyncEventEntity(newId(), a.name, stamped.id, field, value.toString(), hlc.encode(), deviceId, pushed = false)
            }
            db.sync().insert(events)
            a.dao.put(stamped)
            stamped
        }
        onLocalChange()
        return result
    }

    /** Suppression logique (tombstone) : le champ deleted passe à vrai et se propage. */
    suspend fun <T : Syncable> delete(row: T) {
        val a = adapterFor(row)
        val obj = json.encodeToJsonElement(a.serializer, row).jsonObject
        save(json.decodeFromJsonElement(a.serializer, JsonObject(obj + ("deleted" to JsonPrimitive(true)))))
    }

    /** Met à jour des champs purement locaux sans journaliser (ex. chemin du fichier). */
    suspend fun <T : Syncable> saveLocal(row: T) {
        adapterFor(row).dao.put(row)
    }

    /** Applique des événements distants avec la règle « dernier écrit gagne par champ ». */
    suspend fun applyRemote(events: List<FieldEvent>): Int {
        var applied = 0
        db.withTransaction {
            events.groupBy { it.entity to it.rowId }.forEach { (key, evs) ->
                val a = byName[key.first] ?: return@forEach
                applied += applyRow(a, key.second, evs)
            }
        }
        return applied
    }

    private suspend fun <T : Syncable> applyRow(a: Adapter<T>, rowId: String, evs: List<FieldEvent>): Int {
        val existing = a.dao.byId(rowId)
        var obj: JsonObject = existing?.let { json.encodeToJsonElement(a.serializer, it).jsonObject }
            ?: a.onRemoteInsert(JsonObject(mapOf("id" to JsonPrimitive(rowId))))
        var changed = 0
        for (e in evs.sortedWith(EventMerge.order)) {
            if (e.field in a.localFields) continue
            clock.observe(Hlc.decode(e.hlc))
            val cur = db.sync().clock(a.name, rowId, e.field)
            if (!EventMerge.wins(e, cur?.hlc, cur?.deviceId)) continue
            db.sync().putClock(FieldClock(a.name, rowId, e.field, e.hlc, e.deviceId))
            obj = JsonObject(obj + (e.field to e.value))
            changed++
        }
        if (changed > 0) a.dao.put(json.decodeFromJsonElement(a.serializer, obj))
        return changed
    }

    /**
     * Instantané complet sous forme d'événements (passage solo → partagé, rotation de clé).
     * Chaque champ garde son horloge gagnante, pour que la fusion reste cohérente.
     */
    suspend fun snapshotEvents(): List<FieldEvent> {
        val out = ArrayList<FieldEvent>()
        for (a in adapters) collect(a, out)
        return out
    }

    private suspend fun <T : Syncable> collect(a: Adapter<T>, out: MutableList<FieldEvent>) {
        for (row in a.dao.allRows()) {
            val obj = json.encodeToJsonElement(a.serializer, row).jsonObject
            val clocks = db.sync().clocks(a.name, row.id).associateBy { it.field }
            for ((field, value) in obj) {
                if (field == "id" || field in a.localFields) continue
                val c = clocks[field] ?: FieldClock(a.name, row.id, field, clock.tick().encode(), deviceId).also { db.sync().putClock(it) }
                out += FieldEvent(newId(), a.name, row.id, field, value, c.hlc, c.deviceId)
            }
        }
    }

    suspend fun pending(limit: Int = 500): List<SyncEventEntity> = db.sync().unpushed(limit)
    suspend fun markPushed(ids: List<String>) = db.sync().markPushed(ids)

    fun toFieldEvent(e: SyncEventEntity): FieldEvent =
        FieldEvent(e.eventId, e.entity, e.rowId, e.field, json.parseToJsonElement(e.value), e.hlc, e.deviceId)
}
