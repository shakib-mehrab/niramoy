package com.niramoy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MedicineEntity::class,
        DoseLogEntity::class,
        HealthLogEntity::class,
        VisitEntity::class,
        DoctorEntity::class,
        RecordFileEntity::class,
        ContactEntity::class,
        SettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun healthLogDao(): HealthLogDao
    abstract fun visitDao(): VisitDao
    abstract fun doctorDao(): DoctorDao
    abstract fun recordDao(): RecordDao
    abstract fun contactDao(): ContactDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "niramoy_db")
                    // The cloud holds every row, so a schema reset is recovered by the next sync.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
