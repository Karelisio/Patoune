package com.karelisio.patoune.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Toutes les entités synchronisables ont un id UUID, l'auteur (prénom), un horodatage
 * et un drapeau de suppression (tombstone). Chaque champ a une valeur par défaut pour
 * pouvoir être reconstruit champ par champ depuis le journal d'événements.
 */
interface Syncable {
    val id: String
    val createdBy: String
    val updatedAt: Long
    val deleted: Boolean
}

@Serializable
@Entity(tableName = "animals")
data class Animal(
    @PrimaryKey override val id: String = "",
    val name: String = "",
    val species: String = "Chat",
    val breed: String = "",
    val sex: String = "",
    val birthDate: String? = null,
    val chipNumber: String = "",
    val sterilized: Boolean = false,
    val photoMediaId: String? = null,
    val vetName: String = "",
    val vetPhone: String = "",
    val emergencyVetName: String = "",
    val emergencyVetPhone: String = "",
    val allergies: String = "",
    val history: String = "",
    /** Seuil de fréquence respiratoire (resp/min) fixé avec le véto. */
    val breathThreshold: Int? = 30,
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

enum class MedType(val label: String, val unit: String) {
    INHALER("Inhalateur avec chambre", "bouffée"),
    TABLET("Comprimé", "comprimé"),
    LIQUID("Liquide", "mL"),
    OTHER("Autre", "dose"),
}

@Serializable
@Entity(tableName = "medications", indices = [Index("animalId")])
data class Medication(
    @PrimaryKey override val id: String = "",
    val animalId: String = "",
    val name: String = "",
    val type: MedType = MedType.INHALER,
    val dose: String = "",
    /** Unités consommées par prise (bouffées, comprimés…). */
    val unitsPerDose: Double = 1.0,
    /** Horaires "HH:mm" séparés par des virgules. */
    val times: String = "08:00,20:00",
    val startDate: String = "",
    val endDate: String? = null,
    val notes: String = "",
    val stockRemaining: Double? = null,
    val stockAlertAt: Double? = null,
    val renewalDate: String? = null,
    val asNeeded: Boolean = false,
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

enum class DoseStatus { GIVEN, SKIPPED }

@Serializable
@Entity(tableName = "dose_logs", indices = [Index("medicationId"), Index("animalId")])
data class DoseLog(
    @PrimaryKey override val id: String = "",
    val medicationId: String = "",
    val animalId: String = "",
    val scheduledAt: Long? = null,
    val takenAt: Long = 0,
    val status: DoseStatus = DoseStatus.GIVEN,
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

enum class BreathContext(val label: String) { SLEEP("Sommeil"), REST("Repos"), OTHER("Autre") }

@Serializable
@Entity(tableName = "breath_counts", indices = [Index("animalId")])
data class BreathCount(
    @PrimaryKey override val id: String = "",
    val animalId: String = "",
    val at: Long = 0,
    val taps: Int = 0,
    val durationSec: Int = 30,
    val bpm: Double = 0.0,
    val context: BreathContext = BreathContext.SLEEP,
    val note: String = "",
    /** Vrai si issu du comptage auto (estimation validée par l'utilisateur). */
    val estimated: Boolean = false,
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "crises", indices = [Index("animalId")])
data class Crisis(
    @PrimaryKey override val id: String = "",
    val animalId: String = "",
    val startAt: Long = 0,
    val durationSec: Int = 0,
    val intensity: Int = 3,
    /** Types séparés par des virgules (voir CrisisTypes). */
    val types: String = "",
    val emergencyTreatment: String = "",
    val triggers: String = "",
    val notes: String = "",
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

object CrisisTypes {
    val all = listOf("Toux", "Respiration bouche ouverte", "Sifflement", "Respiration abdominale", "Haut-le-cœur", "Autre")
    val triggers = listOf("Litière", "Parfum", "Ménage", "Fumée", "Pollens", "Saison", "Stress")
}

enum class MediaKind { PHOTO, VIDEO, PDF }
enum class AttachType(val label: String) {
    NONE("Libre"), ANIMAL("Profil"), CRISIS("Crise"), BREATH("Comptage"), VISIT("Visite"),
    OBSERVATION("Observation"), DOCUMENT("Document")
}

@Serializable
@Entity(tableName = "media", indices = [Index("animalId"), Index("attachedId")])
data class MediaItem(
    @PrimaryKey override val id: String = "",
    val animalId: String = "",
    val kind: MediaKind = MediaKind.PHOTO,
    val mime: String = "image/jpeg",
    val sizeBytes: Long = 0,
    val durationMs: Long = 0,
    val attachedType: AttachType = AttachType.NONE,
    val attachedId: String? = null,
    val takenAt: Long = 0,
    /** Chemin local (non synchronisé). */
    val localPath: String? = null,
    /** Envoyé chiffré sur R2 (non synchronisé). */
    val uploaded: Boolean = false,
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

enum class ObsCategory(val label: String, val options: List<String>) {
    APPETITE("Appétit", listOf("Normal", "Diminué", "Absent", "Augmenté")),
    WATER("Eau", listOf("Normale", "Peu", "Beaucoup")),
    LITTER("Litière", listOf("Normale", "Diarrhée", "Constipation", "Urine ++", "Rien")),
    VOMIT("Vomissement", listOf("Aucun", "Boule de poils", "Nourriture", "Bile")),
    BEHAVIOR("Comportement", listOf("Normal", "Calme", "Abattu", "Agité", "Se cache")),
}

@Serializable
@Entity(tableName = "observations", indices = [Index("animalId")])
data class Observation(
    @PrimaryKey override val id: String = "",
    val animalId: String = "",
    val at: Long = 0,
    val category: ObsCategory = ObsCategory.APPETITE,
    val value: String = "",
    val note: String = "",
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

enum class CareKind(val label: String) { VACCINE("Vaccin"), DEWORM("Vermifuge"), FLEA("Antipuces") }

@Serializable
@Entity(tableName = "care_items", indices = [Index("animalId")])
data class CareItem(
    @PrimaryKey override val id: String = "",
    val animalId: String = "",
    val kind: CareKind = CareKind.VACCINE,
    val name: String = "",
    val date: String = "",
    val nextDue: String? = null,
    val notes: String = "",
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "vet_visits", indices = [Index("animalId")])
data class VetVisit(
    @PrimaryKey override val id: String = "",
    val animalId: String = "",
    val date: String = "",
    val reason: String = "",
    val report: String = "",
    val nextVisit: String? = null,
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "weights", indices = [Index("animalId")])
data class WeightEntry(
    @PrimaryKey override val id: String = "",
    val animalId: String = "",
    val date: String = "",
    val kg: Double = 0.0,
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

enum class DocKind(val label: String) { PRESCRIPTION("Ordonnance"), ANALYSIS("Analyse"), OTHER("Autre") }

@Serializable
@Entity(tableName = "documents", indices = [Index("animalId")])
data class HealthDocument(
    @PrimaryKey override val id: String = "",
    val animalId: String = "",
    val title: String = "",
    val kind: DocKind = DocKind.PRESCRIPTION,
    val date: String = "",
    val mediaId: String? = null,
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

/** Membre du foyer (synchronisé : le prénom ne passe jamais en clair par le serveur). */
@Serializable
@Entity(tableName = "members")
data class Member(
    @PrimaryKey override val id: String = "",
    val name: String = "",
    val isCreator: Boolean = false,
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

// ---------- Tables locales (non synchronisées) ----------

/** Journal d'événements : un changement de champ = un événement. */
@Entity(tableName = "sync_events", indices = [Index("pushed")])
data class SyncEventEntity(
    @PrimaryKey val eventId: String,
    val entity: String,
    val rowId: String,
    val field: String,
    val value: String,
    val hlc: String,
    val deviceId: String,
    val pushed: Boolean,
)

/** Horloge gagnante actuelle par champ (fusion LWW). */
@Entity(tableName = "field_clocks", primaryKeys = ["entity", "rowId", "field"])
data class FieldClock(
    val entity: String,
    val rowId: String,
    val field: String,
    val hlc: String,
    val deviceId: String,
)

/** Pollens et qualité de l'air du jour (Open-Meteo), par ville choisie. */
@Entity(tableName = "air_days")
data class AirDay(
    @PrimaryKey val date: String,
    val city: String,
    val aqi: Double?,
    val alder: Double?,
    val birch: Double?,
    val grass: Double?,
    val mugwort: Double?,
    val olive: Double?,
    val ragweed: Double?,
) {
    val pollenMax: Double? get() = listOfNotNull(alder, birch, grass, mugwort, olive, ragweed).maxOrNull()
}
