package com.karelisio.patoune.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Animal::class, Medication::class, DoseLog::class, BreathCount::class, Crisis::class,
        MediaItem::class, Observation::class, CareItem::class, VetVisit::class, WeightEntry::class,
        HealthDocument::class, Member::class, SyncEventEntity::class, FieldClock::class, AirDay::class,
        MedicationChange::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class PatouneDb : RoomDatabase() {
    abstract fun animals(): AnimalDao
    abstract fun medications(): MedicationDao
    abstract fun medicationChanges(): MedicationChangeDao
    abstract fun doses(): DoseLogDao
    abstract fun breaths(): BreathDao
    abstract fun crises(): CrisisDao
    abstract fun media(): MediaDao
    abstract fun observations(): ObservationDao
    abstract fun care(): CareDao
    abstract fun visits(): VisitDao
    abstract fun weights(): WeightDao
    abstract fun documents(): DocumentDao
    abstract fun members(): MemberDao
    abstract fun sync(): SyncEventDao
    abstract fun air(): AirDao

    companion object {
        fun build(context: Context, name: String? = "patoune.db"): PatouneDb {
            val builder = if (name == null) Room.inMemoryDatabaseBuilder(context, PatouneDb::class.java)
            else Room.databaseBuilder(context, PatouneDb::class.java, name)
            return builder.build()
        }
    }
}
