package com.niramoy.model

import com.niramoy.data.local.DoseLogEntity
import com.niramoy.data.local.MedicineEntity
import com.niramoy.data.local.SettingsEntity
import com.niramoy.util.Dates

/**
 * Doses are reminded in groups: all medicines due on the same date, slot and meal timing share
 * one alarm ("সকালের খাবারের পরে ওষুধ খান" + the list), which is also one voice phrase.
 */
data class DoseGroup(val date: String, val slot: Slot, val timing: MealTiming) {
    val key: String get() = "$date|${slot.id}|${timing.id}"

    companion object {
        fun parse(key: String): DoseGroup? {
            val p = key.split('|')
            if (p.size != 3) return null
            return DoseGroup(p[0], Slot.of(p[1]) ?: return null, MealTiming.of(p[2]))
        }
    }
}

/** One medicine in one group, with its log (null = not marked yet). */
data class DoseItem(val medicine: MedicineEntity, val group: DoseGroup, val log: DoseLogEntity?) {
    val status: DoseStatus? get() = DoseStatus.of(log?.status)
}

/** Pure scheduling rules (unit-tested in DoseScheduleTest). */
object DoseSchedule {

    fun groupsOn(date: String, meds: List<MedicineEntity>): Map<DoseGroup, List<MedicineEntity>> {
        val out = linkedMapOf<DoseGroup, MutableList<MedicineEntity>>()
        meds.filter { it.isScheduledOn(date) }.forEach { m ->
            m.slotSet.forEach { slot -> out.getOrPut(DoseGroup(date, slot, m.timing)) { mutableListOf() } += m }
        }
        return out
    }

    fun timeOf(g: DoseGroup, s: SettingsEntity): Long = Dates.atMinute(g.date, s.doseTime(g.slot, g.timing))

    /** After this moment an unanswered dose is logged as missed (last reminder + one interval). */
    fun deadline(g: DoseGroup, s: SettingsEntity): Long =
        timeOf(g, s) + s.remindCount.coerceAtLeast(1) * s.remindInterval.coerceAtLeast(1) * 60_000L

    fun pending(g: DoseGroup, meds: List<MedicineEntity>, logs: List<DoseLogEntity>): List<MedicineEntity> {
        val logged = logs.filter { it.date == g.date && it.slot == g.slot.id }.map { it.medicineId }.toSet()
        return meds.filter { it.id !in logged }
    }

    /** Today's doses in time order, each with its log. */
    fun itemsOn(date: String, meds: List<MedicineEntity>, logs: List<DoseLogEntity>, s: SettingsEntity): List<DoseItem> {
        val byId = logs.filter { it.date == date }.associateBy { "${it.medicineId}_${it.slot}" }
        return groupsOn(date, meds).entries
            .sortedBy { timeOf(it.key, s) }
            .flatMap { (g, list) -> list.map { DoseItem(it, g, byId["${it.id}_${g.slot.id}"]) } }
    }

    /**
     * Doses whose reminder sequence is over without an answer. Doses scheduled before the medicine
     * was added are never counted (adding a medicine at 10 am doesn't "miss" its 8 am dose).
     */
    fun overdue(dates: List<String>, meds: List<MedicineEntity>, logs: List<DoseLogEntity>, s: SettingsEntity, now: Long): List<Pair<DoseGroup, MedicineEntity>> =
        dates.flatMap { date ->
            groupsOn(date, meds).flatMap { (g, list) ->
                if (deadline(g, s) > now) emptyList()
                else pending(g, list, logs).filter { it.createdAt < timeOf(g, s) }.map { g to it }
            }
        }

    /** Taken share of all answered/missed doses in [dates] (null when nothing to measure yet). */
    fun adherence(logs: List<DoseLogEntity>, dates: Collection<String>, medicineId: String? = null): Int? {
        val relevant = logs.filter { it.date in dates && (medicineId == null || it.medicineId == medicineId) }
        if (relevant.isEmpty()) return null
        val taken = relevant.count { it.status == DoseStatus.TAKEN.id }
        return (taken * 100f / relevant.size).toInt()
    }
}
