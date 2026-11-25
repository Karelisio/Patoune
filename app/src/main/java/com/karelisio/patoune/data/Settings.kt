package com.karelisio.patoune.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

enum class SyncMode { SOLO, SHARED }

enum class PushType(val label: String, val description: String) {
    DOSE("Prise donnée", "Quand un membre marque une prise (le rappel s'annule)"),
    BREATH("Alerte respiratoire", "Fréquence au-dessus du seuil fixé avec le véto"),
    CRISIS("Crise enregistrée", "Quand un membre enregistre une crise"),
    STOCK("Stock bas", "Quand un médicament arrive en fin de stock"),
}

data class AppSettings(
    val mode: SyncMode = SyncMode.SOLO,
    val onboardingDone: Boolean = false,
    val selectedAnimalId: String? = null,
    val memberName: String = "",
    val deviceId: String = "",
    val memberId: String? = null,
    val householdId: String? = null,
    val serverUrl: String = "",
    val isCreator: Boolean = false,
    val keyEpoch: Int = 0,
    val syncCursor: Long = 0,
    val pushEnabled: Set<PushType> = PushType.entries.toSet(),
    val lastUpdateCheck: Long = 0,
    val city: String? = null,
    val cityLat: Double? = null,
    val cityLon: Double? = null,
    val breathDuration: Int = 30,
    val lastSyncAt: Long = 0,
    val quotaUsed: Long = 0,
    val quotaMax: Long = 0,
) {
    val shared: Boolean get() = mode == SyncMode.SHARED && householdId != null
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class Settings(private val context: Context) {
    private object K {
        val mode = stringPreferencesKey("mode")
        val onboarding = booleanPreferencesKey("onboarding")
        val animal = stringPreferencesKey("animal")
        val memberName = stringPreferencesKey("member_name")
        val deviceId = stringPreferencesKey("device_id")
        val memberId = stringPreferencesKey("member_id")
        val household = stringPreferencesKey("household")
        val server = stringPreferencesKey("server")
        val creator = booleanPreferencesKey("creator")
        val epoch = intPreferencesKey("epoch")
        val cursor = longPreferencesKey("cursor")
        val push = stringPreferencesKey("push")
        val updateCheck = longPreferencesKey("update_check")
        val city = stringPreferencesKey("city")
        val lat = doublePreferencesKey("lat")
        val lon = doublePreferencesKey("lon")
        val breathDuration = intPreferencesKey("breath_duration")
        val lastSync = longPreferencesKey("last_sync")
        val quotaUsed = longPreferencesKey("quota_used")
        val quotaMax = longPreferencesKey("quota_max")
        val token = stringPreferencesKey("token_enc")
        val keyring = stringPreferencesKey("keyring_enc")
        val ecPrivate = stringPreferencesKey("ec_private_enc")
        val ecPublic = stringPreferencesKey("ec_public")
        val creatorPublic = stringPreferencesKey("creator_public")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            mode = p[K.mode]?.let { runCatching { SyncMode.valueOf(it) }.getOrNull() } ?: SyncMode.SOLO,
            onboardingDone = p[K.onboarding] ?: false,
            selectedAnimalId = p[K.animal],
            memberName = p[K.memberName] ?: "",
            deviceId = p[K.deviceId] ?: "",
            memberId = p[K.memberId],
            householdId = p[K.household],
            serverUrl = p[K.server] ?: "",
            isCreator = p[K.creator] ?: false,
            keyEpoch = p[K.epoch] ?: 0,
            syncCursor = p[K.cursor] ?: 0,
            pushEnabled = p[K.push]?.split(",")?.mapNotNull { runCatching { PushType.valueOf(it) }.getOrNull() }?.toSet()
                ?: PushType.entries.toSet(),
            lastUpdateCheck = p[K.updateCheck] ?: 0,
            city = p[K.city], cityLat = p[K.lat], cityLon = p[K.lon],
            breathDuration = p[K.breathDuration] ?: 30,
            lastSyncAt = p[K.lastSync] ?: 0,
            quotaUsed = p[K.quotaUsed] ?: 0, quotaMax = p[K.quotaMax] ?: 0,
        )
    }

    suspend fun current(): AppSettings = flow.first()

    /** Identifiant d'appareil aléatoire, créé au premier accès. */
    suspend fun deviceId(): String {
        current().deviceId.takeIf { it.isNotEmpty() }?.let { return it }
        val id = UUID.randomUUID().toString().take(8)
        context.dataStore.edit { it[K.deviceId] = id }
        return id
    }

    suspend fun setOnboardingDone() = context.dataStore.edit { it[K.onboarding] = true }
    suspend fun selectAnimal(id: String?) = context.dataStore.edit { if (id == null) it.remove(K.animal) else it[K.animal] = id }
    suspend fun setMemberName(name: String) = context.dataStore.edit { it[K.memberName] = name }
    suspend fun setPush(types: Set<PushType>) = context.dataStore.edit { it[K.push] = types.joinToString(",") { t -> t.name } }
    suspend fun setUpdateCheck(t: Long) = context.dataStore.edit { it[K.updateCheck] = t }
    suspend fun setCity(name: String, lat: Double, lon: Double) = context.dataStore.edit { it[K.city] = name; it[K.lat] = lat; it[K.lon] = lon }
    suspend fun setBreathDuration(s: Int) = context.dataStore.edit { it[K.breathDuration] = s }
    suspend fun setCursor(c: Long) = context.dataStore.edit { it[K.cursor] = c }
    suspend fun setLastSync(t: Long) = context.dataStore.edit { it[K.lastSync] = t }
    suspend fun setQuota(used: Long, max: Long) = context.dataStore.edit { it[K.quotaUsed] = used; it[K.quotaMax] = max }
    suspend fun setServerUrl(url: String) = context.dataStore.edit { it[K.server] = url }

    suspend fun enterHousehold(householdId: String, memberId: String, serverUrl: String, creator: Boolean, epoch: Int,
                               tokenEnc: String, keyringEnc: String, creatorPublic: String?) = context.dataStore.edit {
        it[K.mode] = SyncMode.SHARED.name
        it[K.household] = householdId
        it[K.memberId] = memberId
        it[K.server] = serverUrl
        it[K.creator] = creator
        it[K.epoch] = epoch
        it[K.cursor] = 0
        it[K.token] = tokenEnc
        it[K.keyring] = keyringEnc
        if (creatorPublic != null) it[K.creatorPublic] = creatorPublic
    }

    suspend fun leaveHousehold() = context.dataStore.edit {
        it[K.mode] = SyncMode.SOLO.name
        listOf(K.household, K.memberId, K.token, K.keyring, K.creatorPublic).forEach { k -> it.remove(k) }
        it.remove(K.creator); it.remove(K.epoch); it.remove(K.cursor); it.remove(K.quotaUsed); it.remove(K.quotaMax)
    }

    suspend fun setKeyring(epoch: Int, keyringEnc: String) = context.dataStore.edit { it[K.epoch] = epoch; it[K.keyring] = keyringEnc }

    suspend fun secrets(): StoredSecrets = context.dataStore.data.first().let {
        StoredSecrets(it[K.token], it[K.keyring], it[K.ecPrivate], it[K.ecPublic], it[K.creatorPublic])
    }

    suspend fun setDeviceKeys(privateEnc: String, publicB64: String) = context.dataStore.edit { it[K.ecPrivate] = privateEnc; it[K.ecPublic] = publicB64 }
}

data class StoredSecrets(val tokenEnc: String?, val keyringEnc: String?, val ecPrivateEnc: String?, val ecPublic: String?, val creatorPublic: String?)
