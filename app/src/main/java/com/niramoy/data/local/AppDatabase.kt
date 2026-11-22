package com.niramoy.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 3,
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

        /** 1.1: spoken-reminder language. Keeps every existing row (guest users have no cloud copy). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN voiceLanguage TEXT NOT NULL DEFAULT 'en'")
                // Existing users keep hearing the language they already use.
                db.execSQL("UPDATE settings SET voiceLanguage = language")
            }
        }

        /** 1.2: medicine stock (count down on every "taken", low-stock alerts for the family). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicines ADD COLUMN stock REAL")
                db.execSQL("ALTER TABLE medicines ADD COLUMN perDose REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE medicines ADD COLUMN lowStockAt REAL NOT NULL DEFAULT 3.0")
                db.execSQL("ALTER TABLE medicines ADD COLUMN lowStockNotified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE records ADD COLUMN shareUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "niramoy_db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // The cloud holds every row, so a schema reset is recovered by the next sync.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
