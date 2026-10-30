package com.niramoy.data

import com.niramoy.data.local.*

/** Firestore <-> Room. Firestore docs mirror the Room columns; the doc id is the row id. */
object Mappers {
    private fun Map<String, Any?>.str(k: String, def: String = "") = (this[k] as? String) ?: def
    private fun Map<String, Any?>.strOrNull(k: String) = this[k] as? String
    private fun Map<String, Any?>.long(k: String, def: Long = 0L) = (this[k] as? Number)?.toLong() ?: def
    private fun Map<String, Any?>.int(k: String, def: Int = 0) = (this[k] as? Number)?.toInt() ?: def
    private fun Map<String, Any?>.intOrNull(k: String) = (this[k] as? Number)?.toInt()
    private fun Map<String, Any?>.dblOrNull(k: String) = (this[k] as? Number)?.toDouble()
    private fun Map<String, Any?>.bool(k: String, def: Boolean = false) = (this[k] as? Boolean) ?: def

    fun MedicineEntity.toMap(): Map<String, Any?> = mapOf(
        "name" to name, "dose" to dose, "mealTiming" to mealTiming, "slots" to slots,
        "durationType" to durationType, "courseDays" to courseDays, "startDate" to startDate,
        "notes" to notes, "active" to active, "createdAt" to createdAt, "updatedAt" to updatedAt, "deleted" to deleted
    )

    fun medicine(id: String, m: Map<String, Any?>) = MedicineEntity(
        id = id, name = m.str("name"), dose = m.str("dose"), mealTiming = m.str("mealTiming", "after"),
        slots = m.str("slots"), durationType = m.str("durationType", "continuous"), courseDays = m.int("courseDays"),
        startDate = m.str("startDate"), notes = m.str("notes"), active = m.bool("active", true),
        createdAt = m.long("createdAt"), updatedAt = m.long("updatedAt"), deleted = m.bool("deleted")
    )

    fun DoseLogEntity.toMap(): Map<String, Any?> = mapOf(
        "medicineId" to medicineId, "date" to date, "slot" to slot, "status" to status, "at" to at, "updatedAt" to updatedAt
    )

    fun doseLog(id: String, m: Map<String, Any?>) = DoseLogEntity(
        id = id, medicineId = m.str("medicineId"), date = m.str("date"), slot = m.str("slot"),
        status = m.str("status"), at = m.long("at"), updatedAt = m.long("updatedAt")
    )

    fun HealthLogEntity.toMap(): Map<String, Any?> = mapOf(
        "at" to at, "date" to date, "mood" to mood, "sugar" to sugar, "sugarTag" to sugarTag,
        "systolic" to systolic, "diastolic" to diastolic, "pulse" to pulse, "weight" to weight,
        "temperature" to temperature, "note" to note, "updatedAt" to updatedAt, "deleted" to deleted
    )

    fun healthLog(id: String, m: Map<String, Any?>) = HealthLogEntity(
        id = id, at = m.long("at"), date = m.str("date"), mood = m.intOrNull("mood"), sugar = m.dblOrNull("sugar"),
        sugarTag = m.strOrNull("sugarTag"), systolic = m.intOrNull("systolic"), diastolic = m.intOrNull("diastolic"),
        pulse = m.intOrNull("pulse"), weight = m.dblOrNull("weight"), temperature = m.dblOrNull("temperature"),
        note = m.str("note"), updatedAt = m.long("updatedAt"), deleted = m.bool("deleted")
    )

    fun VisitEntity.toMap(): Map<String, Any?> = mapOf(
        "date" to date, "doctorName" to doctorName, "notes" to notes, "nextDate" to nextDate,
        "remindNext" to remindNext, "updatedAt" to updatedAt, "deleted" to deleted
    )

    fun visit(id: String, m: Map<String, Any?>) = VisitEntity(
        id = id, date = m.str("date"), doctorName = m.str("doctorName"), notes = m.str("notes"),
        nextDate = m.strOrNull("nextDate"), remindNext = m.bool("remindNext", true),
        updatedAt = m.long("updatedAt"), deleted = m.bool("deleted")
    )

    fun DoctorEntity.toMap(): Map<String, Any?> = mapOf(
        "name" to name, "speciality" to speciality, "phone" to phone, "address" to address,
        "updatedAt" to updatedAt, "deleted" to deleted
    )

    fun doctor(id: String, m: Map<String, Any?>) = DoctorEntity(
        id = id, name = m.str("name"), speciality = m.str("speciality"), phone = m.str("phone"),
        address = m.str("address"), updatedAt = m.long("updatedAt"), deleted = m.bool("deleted")
    )

    /** localPath is device-specific and never synced. */
    fun RecordFileEntity.toMap(): Map<String, Any?> = mapOf(
        "kind" to kind, "date" to date, "title" to title, "doctorName" to doctorName, "reportType" to reportType,
        "remotePath" to remotePath, "mime" to mime, "updatedAt" to updatedAt, "deleted" to deleted
    )

    fun record(id: String, m: Map<String, Any?>, localPath: String) = RecordFileEntity(
        id = id, kind = m.str("kind", "prescription"), date = m.str("date"), title = m.str("title"),
        doctorName = m.str("doctorName"), reportType = m.str("reportType"), localPath = localPath,
        remotePath = m.str("remotePath"), mime = m.str("mime", "image/jpeg"),
        updatedAt = m.long("updatedAt"), deleted = m.bool("deleted")
    )

    fun ContactEntity.toMap(): Map<String, Any?> = mapOf(
        "name" to name, "relation" to relation, "phone" to phone, "sortOrder" to sortOrder,
        "updatedAt" to updatedAt, "deleted" to deleted
    )

    fun contact(id: String, m: Map<String, Any?>) = ContactEntity(
        id = id, name = m.str("name"), relation = m.str("relation"), phone = m.str("phone"),
        sortOrder = m.int("sortOrder"), updatedAt = m.long("updatedAt"), deleted = m.bool("deleted")
    )

    fun SettingsEntity.toMap(): Map<String, Any?> = mapOf(
        "language" to language, "name" to name, "bloodGroup" to bloodGroup, "allergies" to allergies,
        "wakeTime" to wakeTime, "breakfastTime" to breakfastTime, "lunchTime" to lunchTime,
        "dinnerTime" to dinnerTime, "sleepTime" to sleepTime, "beforeMealOffset" to beforeMealOffset,
        "afterMealOffset" to afterMealOffset, "remindCount" to remindCount, "remindInterval" to remindInterval,
        "snoozeMinutes" to snoozeMinutes, "tone" to tone, "voiceLanguage" to voiceLanguage, "onboarded" to onboarded, "updatedAt" to updatedAt
    )

    fun settings(m: Map<String, Any?>): SettingsEntity {
        val d = SettingsEntity()
        return SettingsEntity(
            language = m.str("language", d.language), name = m.str("name"), bloodGroup = m.str("bloodGroup"),
            allergies = m.str("allergies"), wakeTime = m.int("wakeTime", d.wakeTime),
            breakfastTime = m.int("breakfastTime", d.breakfastTime), lunchTime = m.int("lunchTime", d.lunchTime),
            dinnerTime = m.int("dinnerTime", d.dinnerTime), sleepTime = m.int("sleepTime", d.sleepTime),
            beforeMealOffset = m.int("beforeMealOffset", d.beforeMealOffset),
            afterMealOffset = m.int("afterMealOffset", d.afterMealOffset),
            remindCount = m.int("remindCount", d.remindCount), remindInterval = m.int("remindInterval", d.remindInterval),
            snoozeMinutes = m.int("snoozeMinutes", d.snoozeMinutes), tone = m.str("tone", d.tone),
            voiceLanguage = m.str("voiceLanguage", m.str("language", d.voiceLanguage)),
            onboarded = m.bool("onboarded", true), updatedAt = m.long("updatedAt")
        )
    }
}
