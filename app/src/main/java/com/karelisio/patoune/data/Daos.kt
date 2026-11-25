package com.karelisio.patoune.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Opérations communes utilisées par la sync, la sauvegarde et la migration. */
interface SyncDao<T : Syncable> {
    suspend fun byId(id: String): T?
    suspend fun allRows(): List<T>
    suspend fun put(row: T)
}

@Dao
interface AnimalDao : SyncDao<Animal> {
    @Query("SELECT * FROM animals WHERE deleted = 0 ORDER BY name") fun observeAll(): Flow<List<Animal>>
    @Query("SELECT * FROM animals WHERE id = :id") fun observe(id: String): Flow<Animal?>
    @Query("SELECT * FROM animals WHERE id = :id") override suspend fun byId(id: String): Animal?
    @Query("SELECT * FROM animals") override suspend fun allRows(): List<Animal>
    @Upsert override suspend fun put(row: Animal)
}

@Dao
interface MedicationDao : SyncDao<Medication> {
    @Query("SELECT * FROM medications WHERE animalId = :animalId AND deleted = 0 ORDER BY name") fun observeFor(animalId: String): Flow<List<Medication>>
    @Query("SELECT * FROM medications WHERE deleted = 0") fun observeActive(): Flow<List<Medication>>
    @Query("SELECT * FROM medications WHERE deleted = 0") suspend fun active(): List<Medication>
    @Query("SELECT * FROM medications WHERE id = :id") fun observe(id: String): Flow<Medication?>
    @Query("SELECT * FROM medications WHERE id = :id") override suspend fun byId(id: String): Medication?
    @Query("SELECT * FROM medications") override suspend fun allRows(): List<Medication>
    @Upsert override suspend fun put(row: Medication)
}

@Dao
interface DoseLogDao : SyncDao<DoseLog> {
    @Query("SELECT * FROM dose_logs WHERE medicationId = :medId AND deleted = 0 ORDER BY takenAt DESC") fun observeFor(medId: String): Flow<List<DoseLog>>
    @Query("SELECT * FROM dose_logs WHERE animalId = :animalId AND deleted = 0 AND takenAt BETWEEN :from AND :to ORDER BY takenAt") suspend fun between(animalId: String, from: Long, to: Long): List<DoseLog>
    @Query("SELECT * FROM dose_logs WHERE medicationId = :medId AND deleted = 0 AND takenAt >= :from ORDER BY takenAt DESC") suspend fun since(medId: String, from: Long): List<DoseLog>
    @Query("SELECT * FROM dose_logs WHERE animalId = :animalId AND deleted = 0 AND takenAt >= :from ORDER BY takenAt DESC") fun observeSince(animalId: String, from: Long): Flow<List<DoseLog>>
    @Query("SELECT * FROM dose_logs WHERE id = :id") override suspend fun byId(id: String): DoseLog?
    @Query("SELECT * FROM dose_logs") override suspend fun allRows(): List<DoseLog>
    @Upsert override suspend fun put(row: DoseLog)
}

@Dao
interface BreathDao : SyncDao<BreathCount> {
    @Query("SELECT * FROM breath_counts WHERE animalId = :animalId AND deleted = 0 ORDER BY at") fun observeFor(animalId: String): Flow<List<BreathCount>>
    @Query("SELECT * FROM breath_counts WHERE animalId = :animalId AND deleted = 0 AND at BETWEEN :from AND :to ORDER BY at") suspend fun between(animalId: String, from: Long, to: Long): List<BreathCount>
    @Query("SELECT * FROM breath_counts WHERE deleted = 0 ORDER BY at DESC LIMIT 1") suspend fun latestAny(): BreathCount?
    @Query("SELECT * FROM breath_counts WHERE animalId = :animalId AND deleted = 0 ORDER BY at DESC LIMIT 5") suspend fun latest(animalId: String): List<BreathCount>
    @Query("SELECT * FROM breath_counts WHERE id = :id") override suspend fun byId(id: String): BreathCount?
    @Query("SELECT * FROM breath_counts") override suspend fun allRows(): List<BreathCount>
    @Upsert override suspend fun put(row: BreathCount)
}

@Dao
interface CrisisDao : SyncDao<Crisis> {
    @Query("SELECT * FROM crises WHERE animalId = :animalId AND deleted = 0 ORDER BY startAt DESC") fun observeFor(animalId: String): Flow<List<Crisis>>
    @Query("SELECT * FROM crises WHERE animalId = :animalId AND deleted = 0 AND startAt BETWEEN :from AND :to ORDER BY startAt") suspend fun between(animalId: String, from: Long, to: Long): List<Crisis>
    @Query("SELECT * FROM crises WHERE id = :id") fun observe(id: String): Flow<Crisis?>
    @Query("SELECT * FROM crises WHERE id = :id") override suspend fun byId(id: String): Crisis?
    @Query("SELECT * FROM crises") override suspend fun allRows(): List<Crisis>
    @Upsert override suspend fun put(row: Crisis)
}

@Dao
interface MediaDao : SyncDao<MediaItem> {
    @Query("SELECT * FROM media WHERE animalId = :animalId AND deleted = 0 ORDER BY takenAt DESC") fun observeFor(animalId: String): Flow<List<MediaItem>>
    @Query("SELECT * FROM media WHERE attachedId = :attachedId AND deleted = 0 ORDER BY takenAt") fun observeAttached(attachedId: String): Flow<List<MediaItem>>
    @Query("SELECT * FROM media WHERE attachedId = :attachedId AND deleted = 0 ORDER BY takenAt") suspend fun attached(attachedId: String): List<MediaItem>
    @Query("SELECT * FROM media WHERE uploaded = 0 AND localPath IS NOT NULL AND deleted = 0") suspend fun pendingUpload(): List<MediaItem>
    @Query("SELECT * FROM media WHERE localPath IS NULL AND deleted = 0") suspend fun missingLocal(): List<MediaItem>
    @Query("SELECT * FROM media WHERE id = :id") fun observe(id: String): Flow<MediaItem?>
    @Query("SELECT * FROM media WHERE id = :id") override suspend fun byId(id: String): MediaItem?
    @Query("SELECT * FROM media") override suspend fun allRows(): List<MediaItem>
    @Upsert override suspend fun put(row: MediaItem)
}

@Dao
interface ObservationDao : SyncDao<Observation> {
    @Query("SELECT * FROM observations WHERE animalId = :animalId AND deleted = 0 ORDER BY at DESC") fun observeFor(animalId: String): Flow<List<Observation>>
    @Query("SELECT * FROM observations WHERE animalId = :animalId AND deleted = 0 AND at BETWEEN :from AND :to ORDER BY at") suspend fun between(animalId: String, from: Long, to: Long): List<Observation>
    @Query("SELECT * FROM observations WHERE id = :id") override suspend fun byId(id: String): Observation?
    @Query("SELECT * FROM observations") override suspend fun allRows(): List<Observation>
    @Upsert override suspend fun put(row: Observation)
}

@Dao
interface CareDao : SyncDao<CareItem> {
    @Query("SELECT * FROM care_items WHERE animalId = :animalId AND deleted = 0 ORDER BY date DESC") fun observeFor(animalId: String): Flow<List<CareItem>>
    @Query("SELECT * FROM care_items WHERE deleted = 0 AND nextDue IS NOT NULL") suspend fun withDue(): List<CareItem>
    @Query("SELECT * FROM care_items WHERE id = :id") override suspend fun byId(id: String): CareItem?
    @Query("SELECT * FROM care_items") override suspend fun allRows(): List<CareItem>
    @Upsert override suspend fun put(row: CareItem)
}

@Dao
interface VisitDao : SyncDao<VetVisit> {
    @Query("SELECT * FROM vet_visits WHERE animalId = :animalId AND deleted = 0 ORDER BY date DESC") fun observeFor(animalId: String): Flow<List<VetVisit>>
    @Query("SELECT * FROM vet_visits WHERE id = :id") override suspend fun byId(id: String): VetVisit?
    @Query("SELECT * FROM vet_visits") override suspend fun allRows(): List<VetVisit>
    @Upsert override suspend fun put(row: VetVisit)
}

@Dao
interface WeightDao : SyncDao<WeightEntry> {
    @Query("SELECT * FROM weights WHERE animalId = :animalId AND deleted = 0 ORDER BY date") fun observeFor(animalId: String): Flow<List<WeightEntry>>
    @Query("SELECT * FROM weights WHERE animalId = :animalId AND deleted = 0 ORDER BY date") suspend fun forAnimal(animalId: String): List<WeightEntry>
    @Query("SELECT * FROM weights WHERE id = :id") override suspend fun byId(id: String): WeightEntry?
    @Query("SELECT * FROM weights") override suspend fun allRows(): List<WeightEntry>
    @Upsert override suspend fun put(row: WeightEntry)
}

@Dao
interface DocumentDao : SyncDao<HealthDocument> {
    @Query("SELECT * FROM documents WHERE animalId = :animalId AND deleted = 0 ORDER BY date DESC") fun observeFor(animalId: String): Flow<List<HealthDocument>>
    @Query("SELECT * FROM documents WHERE id = :id") override suspend fun byId(id: String): HealthDocument?
    @Query("SELECT * FROM documents") override suspend fun allRows(): List<HealthDocument>
    @Upsert override suspend fun put(row: HealthDocument)
}

@Dao
interface MemberDao : SyncDao<Member> {
    @Query("SELECT * FROM members WHERE deleted = 0 ORDER BY name") fun observeAll(): Flow<List<Member>>
    @Query("SELECT * FROM members WHERE id = :id") override suspend fun byId(id: String): Member?
    @Query("SELECT * FROM members") override suspend fun allRows(): List<Member>
    @Upsert override suspend fun put(row: Member)
}

@Dao
interface SyncEventDao {
    @Upsert suspend fun insert(events: List<SyncEventEntity>)
    @Query("SELECT * FROM sync_events WHERE pushed = 0 ORDER BY hlc LIMIT :limit") suspend fun unpushed(limit: Int): List<SyncEventEntity>
    @Query("UPDATE sync_events SET pushed = 1 WHERE eventId IN (:ids)") suspend fun markPushed(ids: List<String>)
    @Query("UPDATE sync_events SET pushed = 0") suspend fun markAllUnpushed()
    @Query("SELECT COUNT(*) FROM sync_events WHERE pushed = 0") fun observePendingCount(): Flow<Int>
    @Query("SELECT * FROM field_clocks WHERE entity = :entity AND rowId = :rowId AND field = :field") suspend fun clock(entity: String, rowId: String, field: String): FieldClock?
    @Query("SELECT * FROM field_clocks WHERE entity = :entity AND rowId = :rowId") suspend fun clocks(entity: String, rowId: String): List<FieldClock>
    @Upsert suspend fun putClock(clock: FieldClock)
    @Query("DELETE FROM sync_events") suspend fun clearEvents()
}

@Dao
interface AirDao {
    @Upsert suspend fun put(day: AirDay)
    @Query("SELECT * FROM air_days WHERE date BETWEEN :from AND :to ORDER BY date") suspend fun between(from: String, to: String): List<AirDay>
    @Query("SELECT * FROM air_days ORDER BY date") fun observeAll(): Flow<List<AirDay>>
    @Query("SELECT * FROM air_days ORDER BY date DESC LIMIT 1") suspend fun latest(): AirDay?
}
