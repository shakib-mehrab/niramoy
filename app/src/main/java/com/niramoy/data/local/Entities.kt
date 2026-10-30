package com.niramoy.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.niramoy.model.DurationType
import com.niramoy.model.MealTiming
import com.niramoy.model.MedState
import com.niramoy.model.Slot
import com.niramoy.util.Dates

/**
 * Every synced row has a stable string id, an [updatedAt] stamp (last writer wins across devices)
 * and a soft-delete flag, so a delete made offline still reaches the cloud and other devices.
 */
interface Synced {
    val id: String
    val updatedAt: Long
}

@Entity(tableName = "medicines")
data class MedicineEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val dose: String,
    val mealTiming: String,          // MealTiming.id
    val slots: String,               // "morning,noon,night"
    val durationType: String,        // DurationType.id
    val courseDays: Int = 0,
    val startDate: String,           // yyyy-MM-dd
    val notes: String = "",
    val active: Boolean = true,      // false = stopped by the user (history kept, no reminders)
    val createdAt: Long,
    override val updatedAt: Long,
    val deleted: Boolean = false
) : Synced {
    val slotSet: Set<Slot> get() = Slot.parseSet(slots)
    val timing: MealTiming get() = MealTiming.of(mealTiming)
    val duration: DurationType get() = DurationType.of(durationType)

    /** Last day of a course (inclusive), or null for continuous medicines. */
    val endDate: String?
        get() = if (duration == DurationType.COURSE && courseDays > 0) Dates.addDays(startDate, courseDays - 1) else null

    /** True when a dose of this medicine is due on [date]. */
    fun isScheduledOn(date: String): Boolean {
        if (deleted || !active || date < startDate) return false
        val end = endDate
        return end == null || date <= end
    }

    fun state(today: String): MedState = when {
        !active -> MedState.STOPPED
        endDate?.let { today > it } == true -> MedState.COMPLETED
        today < startDate -> MedState.UPCOMING
        else -> MedState.ACTIVE
    }
}

/** One medicine, one slot, one day. id = "<date>_<medicineId>_<slot>" so a dose can only be logged once. */
@Entity(tableName = "dose_logs", indices = [Index("date"), Index("medicineId")])
data class DoseLogEntity(
    @PrimaryKey override val id: String,
    val medicineId: String,
    val date: String,
    val slot: String,
    val status: String,              // DoseStatus.id
    val at: Long,                    // when it was taken / skipped / marked missed
    override val updatedAt: Long
) : Synced {
    companion object {
        fun idOf(date: String, medicineId: String, slot: Slot) = "${date}_${medicineId}_${slot.id}"
    }
}

/** A body-condition entry: any mix of mood, sugar, pressure, weight, temperature and a note. */
@Entity(tableName = "health_logs", indices = [Index("date")])
data class HealthLogEntity(
    @PrimaryKey override val id: String,
    val at: Long,
    val date: String,
    val mood: Int? = null,           // 1 (very bad) .. 5 (very good)
    val sugar: Double? = null,       // mg/dL
    val sugarTag: String? = null,    // SugarTag.id
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val pulse: Int? = null,
    val weight: Double? = null,      // kg
    val temperature: Double? = null, // °F (what Bangladeshi thermometers show)
    val note: String = "",
    override val updatedAt: Long,
    val deleted: Boolean = false
) : Synced

@Entity(tableName = "visits")
data class VisitEntity(
    @PrimaryKey override val id: String,
    val date: String,
    val doctorName: String,
    val notes: String = "",
    val nextDate: String? = null,
    val remindNext: Boolean = true,
    override val updatedAt: Long,
    val deleted: Boolean = false
) : Synced

@Entity(tableName = "doctors")
data class DoctorEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val speciality: String = "",
    val phone: String = "",
    val address: String = "",
    override val updatedAt: Long,
    val deleted: Boolean = false
) : Synced

/** A prescription or test-report image/PDF. The file lives locally and in Supabase Storage. */
@Entity(tableName = "records")
data class RecordFileEntity(
    @PrimaryKey override val id: String,
    val kind: String,                // RecordKind.id
    val date: String,
    val title: String = "",
    val doctorName: String = "",
    val reportType: String = "",
    val localPath: String = "",      // app-private copy (may be empty on a fresh install until downloaded)
    val remotePath: String = "",     // "<uid>/<id>.jpg" in the bucket; empty until uploaded
    val mime: String = "image/jpeg",
    override val updatedAt: Long,
    val deleted: Boolean = false
) : Synced

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val relation: String = "",
    val phone: String,
    val sortOrder: Int = 0,
    override val updatedAt: Long,
    val deleted: Boolean = false
) : Synced

/** Profile, routine and reminder settings (one row, one Firestore doc). Times are minutes since midnight. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val rowId: Int = 1,
    val language: String = "en",
    val name: String = "",
    val bloodGroup: String = "",
    val allergies: String = "",
    val wakeTime: Int = 6 * 60,
    val breakfastTime: Int = 8 * 60,
    val lunchTime: Int = 13 * 60 + 30,
    val dinnerTime: Int = 21 * 60,
    val sleepTime: Int = 22 * 60 + 30,
    val beforeMealOffset: Int = 15,
    val afterMealOffset: Int = 30,
    val remindCount: Int = 3,
    val remindInterval: Int = 10,
    val snoozeMinutes: Int = 10,
    val tone: String = TONE_VOICE,
    /** Language of the spoken reminder ("en" / "bn"), independent of the app language. */
    val voiceLanguage: String = "en",
    val onboarded: Boolean = false,
    val updatedAt: Long = 0L
) {
    fun mealTime(slot: Slot): Int = when (slot) {
        Slot.MORNING -> breakfastTime
        Slot.NOON -> lunchTime
        Slot.NIGHT -> dinnerTime
    }

    /** Reminder time for a slot: before meal = meal - N min, after meal = meal + N min. */
    fun doseTime(slot: Slot, timing: MealTiming): Int {
        val t = if (timing == MealTiming.BEFORE) mealTime(slot) - beforeMealOffset else mealTime(slot) + afterMealOffset
        return t.coerceIn(0, 1439)
    }

    companion object {
        const val TONE_VOICE = "voice"       // spoken phrase for the exact slot ("সকালের খাবারের পরে ওষুধ খান")
        const val TONE_GENERIC = "generic"   // "ওষুধ খাওয়ার সময় হয়েছে"
        const val TONE_BEEP = "beep"
        const val TONE_SYSTEM = "system"     // phone's own alarm sound
        const val TONE_CUSTOM = "custom"     // a sound file the user picked (kept on this phone)
        val TONES = listOf(TONE_VOICE, TONE_GENERIC, TONE_BEEP, TONE_SYSTEM, TONE_CUSTOM)
    }
}
