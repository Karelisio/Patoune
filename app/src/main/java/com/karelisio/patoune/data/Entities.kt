package com.karelisio.patoune.data

import com.karelisio.patoune.R
import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import androidx.room.ColumnInfo
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
    /** Ordre d'affichage choisi par l'utilisateur. */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

/** Les noms d'enum sont stockés et synchronisés : ne jamais les renommer (libellés traduits via les ressources). */
enum class MedType(@StringRes val labelRes: Int, @PluralsRes val unitRes: Int, @StringRes val unitsNameRes: Int) {
    INHALER(R.string.med_type_inhaler, R.plurals.unit_inhaler, R.string.units_inhaler),
    TABLET(R.string.med_type_tablet, R.plurals.unit_tablet, R.string.units_tablet),
    LIQUID(R.string.med_type_liquid, R.plurals.unit_liquid, R.string.units_liquid),
    OTHER(R.string.med_type_other, R.plurals.unit_other, R.string.units_other),
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
    /** Rappel type réveil : alarme système et notification plein écran. */
    @ColumnInfo(defaultValue = "0") val alarmStyle: Boolean = false,
    /** Relance « En retard » si la prise n'est pas marquée après ce délai (0 = jamais). */
    @ColumnInfo(defaultValue = "30") val lateMinutes: Int = 30,
    override val createdBy: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
) : Syncable

/** Changement notable d'un traitement (dose, horaires, dates…), pour l'historique et le rapport véto. */
@Serializable
@Entity(tableName = "medication_changes", indices = [Index("medicationId"), Index("animalId")])
data class MedicationChange(
    @PrimaryKey override val id: String = "",
    val medicationId: String = "",
    val animalId: String = "",
    val at: Long = 0,
    /** Lignes lisibles séparées par des retours à la ligne. */
    val summary: String = "",
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

enum class BreathContext(@StringRes val labelRes: Int) {
    SLEEP(R.string.breath_ctx_sleep), REST(R.string.breath_ctx_rest), OTHER(R.string.breath_ctx_other)
}

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

/**
 * Types et déclencheurs de crise : ces chaînes françaises sont les valeurs stockées et synchronisées
 * (clés stables, ne pas modifier). Seul l'affichage est traduit, via [labels].
 */
object CrisisTypes {
    val all = listOf("Toux", "Respiration bouche ouverte", "Sifflement", "Respiration abdominale", "Haut-le-cœur", "Autre")
    val triggers = listOf("Litière", "Parfum", "Ménage", "Fumée", "Pollens", "Saison", "Stress")

    /** Clé stockée → libellé traduit. Une clé inconnue (ancienne donnée, saisie libre) s'affiche telle quelle. */
    val labels: Map<String, Int> = mapOf(
        "Toux" to R.string.crisis_type_cough,
        "Respiration bouche ouverte" to R.string.crisis_type_open_mouth,
        "Sifflement" to R.string.crisis_type_wheeze,
        "Respiration abdominale" to R.string.crisis_type_abdominal,
        "Haut-le-cœur" to R.string.crisis_type_retch,
        "Autre" to R.string.crisis_type_other,
        "Litière" to R.string.trigger_litter,
        "Parfum" to R.string.trigger_perfume,
        "Ménage" to R.string.trigger_cleaning,
        "Fumée" to R.string.trigger_smoke,
        "Pollens" to R.string.trigger_pollen,
        "Saison" to R.string.trigger_season,
        "Stress" to R.string.trigger_stress,
    )
}

enum class MediaKind { PHOTO, VIDEO, PDF }
enum class AttachType(@StringRes val labelRes: Int) {
    NONE(R.string.attach_none), ANIMAL(R.string.attach_animal), CRISIS(R.string.attach_crisis), BREATH(R.string.attach_breath),
    VISIT(R.string.attach_visit), OBSERVATION(R.string.attach_observation), DOCUMENT(R.string.attach_document)
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

/**
 * [options] : valeurs françaises stockées telles quelles et synchronisées (clés stables, ne pas modifier) ;
 * l'affichage passe par [optionLabels].
 */
enum class ObsCategory(@StringRes val labelRes: Int, val options: List<String>) {
    APPETITE(R.string.obs_appetite, listOf("Normal", "Diminué", "Absent", "Augmenté")),
    WATER(R.string.obs_water, listOf("Normale", "Peu", "Beaucoup")),
    LITTER(R.string.obs_litter, listOf("Normale", "Diarrhée", "Constipation", "Urine ++", "Rien")),
    VOMIT(R.string.obs_vomit, listOf("Aucun", "Boule de poils", "Nourriture", "Bile")),
    BEHAVIOR(R.string.obs_behavior, listOf("Normal", "Calme", "Abattu", "Agité", "Se cache"));

    companion object {
        /** Valeur stockée → libellé traduit ; repli sur la valeur brute si inconnue. */
        val optionLabels: Map<String, Int> = mapOf(
            "Normal" to R.string.obs_opt_normal,
            "Normale" to R.string.obs_opt_normal_f,
            "Diminué" to R.string.obs_opt_decreased,
            "Absent" to R.string.obs_opt_absent,
            "Augmenté" to R.string.obs_opt_increased,
            "Peu" to R.string.obs_opt_little,
            "Beaucoup" to R.string.obs_opt_a_lot,
            "Diarrhée" to R.string.obs_opt_diarrhea,
            "Constipation" to R.string.obs_opt_constipation,
            "Urine ++" to R.string.obs_opt_urine,
            "Rien" to R.string.obs_opt_nothing,
            "Aucun" to R.string.obs_opt_none,
            "Boule de poils" to R.string.obs_opt_hairball,
            "Nourriture" to R.string.obs_opt_food,
            "Bile" to R.string.obs_opt_bile,
            "Calme" to R.string.obs_opt_calm,
            "Abattu" to R.string.obs_opt_lethargic,
            "Agité" to R.string.obs_opt_restless,
            "Se cache" to R.string.obs_opt_hiding,
        )
    }
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

enum class CareKind(@StringRes val labelRes: Int) { VACCINE(R.string.care_vaccine), DEWORM(R.string.care_deworm), FLEA(R.string.care_flea) }

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

enum class DocKind(@StringRes val labelRes: Int) { PRESCRIPTION(R.string.doc_prescription), ANALYSIS(R.string.doc_analysis), OTHER(R.string.doc_other) }


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
