package com.niramoy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.niramoy.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrading the app must never lose data that only lives on the phone (guest users, offline edits).
 * Builds a database exactly as v1.1.x left it (schema version 2), then opens it with the current app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {

    private val v2Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `medicines` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `dose` TEXT NOT NULL, `mealTiming` TEXT NOT NULL, `slots` TEXT NOT NULL, `durationType` TEXT NOT NULL, `courseDays` INTEGER NOT NULL, `startDate` TEXT NOT NULL, `notes` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `dose_logs` (`id` TEXT NOT NULL, `medicineId` TEXT NOT NULL, `date` TEXT NOT NULL, `slot` TEXT NOT NULL, `status` TEXT NOT NULL, `at` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_dose_logs_date` ON `dose_logs` (`date`)",
        "CREATE INDEX IF NOT EXISTS `index_dose_logs_medicineId` ON `dose_logs` (`medicineId`)",
        "CREATE TABLE IF NOT EXISTS `health_logs` (`id` TEXT NOT NULL, `at` INTEGER NOT NULL, `date` TEXT NOT NULL, `mood` INTEGER, `sugar` REAL, `sugarTag` TEXT, `systolic` INTEGER, `diastolic` INTEGER, `pulse` INTEGER, `weight` REAL, `temperature` REAL, `note` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_health_logs_date` ON `health_logs` (`date`)",
        "CREATE TABLE IF NOT EXISTS `visits` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `doctorName` TEXT NOT NULL, `notes` TEXT NOT NULL, `nextDate` TEXT, `remindNext` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `doctors` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `speciality` TEXT NOT NULL, `phone` TEXT NOT NULL, `address` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `records` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `date` TEXT NOT NULL, `title` TEXT NOT NULL, `doctorName` TEXT NOT NULL, `reportType` TEXT NOT NULL, `localPath` TEXT NOT NULL, `remotePath` TEXT NOT NULL, `mime` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `contacts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `relation` TEXT NOT NULL, `phone` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `settings` (`rowId` INTEGER NOT NULL, `language` TEXT NOT NULL, `name` TEXT NOT NULL, `bloodGroup` TEXT NOT NULL, `allergies` TEXT NOT NULL, `wakeTime` INTEGER NOT NULL, `breakfastTime` INTEGER NOT NULL, `lunchTime` INTEGER NOT NULL, `dinnerTime` INTEGER NOT NULL, `sleepTime` INTEGER NOT NULL, `beforeMealOffset` INTEGER NOT NULL, `afterMealOffset` INTEGER NOT NULL, `remindCount` INTEGER NOT NULL, `remindInterval` INTEGER NOT NULL, `snoozeMinutes` INTEGER NOT NULL, `tone` TEXT NOT NULL, `voiceLanguage` TEXT NOT NULL, `onboarded` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`rowId`))"
    )

    @Test fun upgradeFromV2KeepsEveryRow() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("niramoy_db")
        val file = ctx.getDatabasePath("niramoy_db").apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            v2Schema.forEach { db.execSQL(it) }
            db.execSQL("INSERT INTO medicines VALUES ('m1','Thyrox 25','½ tablet','before','morning','continuous',0,'2026-11-01','',1,100,200,0)")
            db.execSQL("INSERT INTO dose_logs VALUES ('2026-11-20_m1_morning','m1','2026-11-20','morning','taken',300,300)")
            db.execSQL("INSERT INTO records VALUES ('r1','prescription','2026-11-01','','Dr. Rahman','','','uid/r1.jpg','image/jpeg',1,0)")
            db.execSQL("INSERT INTO settings VALUES (1,'en','Mohiuddin','','',360,480,810,1260,1350,15,30,3,10,10,'voice','en',1,5)")
            db.version = 2
        }

        val room = AppDatabase.getInstance(ctx)
        val med = room.medicineDao().getAll().single()
        assertEquals("Thyrox 25", med.name)
        assertEquals("½ tablet", med.dose)
        assertNull("stock is not tracked until the user sets it", med.stock)
        assertEquals(1.0, med.perDose, 0.0)
        assertEquals(3.0, med.lowStockAt, 0.0)
        assertFalse(med.lowStockNotified)
        assertEquals("taken", room.doseLogDao().get("2026-11-20_m1_morning")!!.status)
        val rec = room.recordDao().get("r1")!!
        assertEquals("Dr. Rahman", rec.doctorName)
        assertEquals("", rec.shareUrl)
        assertEquals("Mohiuddin", room.settingsDao().get()!!.name)
        room.close()
    }
}
