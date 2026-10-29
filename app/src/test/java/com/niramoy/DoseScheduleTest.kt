package com.niramoy

import com.niramoy.data.local.DoseLogEntity
import com.niramoy.data.local.MedicineEntity
import com.niramoy.data.local.SettingsEntity
import com.niramoy.model.*
import com.niramoy.util.Dates
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class DoseScheduleTest {
    @Before fun dhaka() { TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka")) }

    private val s = SettingsEntity(breakfastTime = 8 * 60, lunchTime = 13 * 60 + 30, dinnerTime = 21 * 60,
        beforeMealOffset = 15, afterMealOffset = 30, remindCount = 3, remindInterval = 10)

    private fun med(id: String, slots: String, timing: String = "after", type: String = "continuous", days: Int = 0,
                    start: String = "2026-10-01", created: Long = 0L, active: Boolean = true) =
        MedicineEntity(id, id, "1", timing, slots, type, days, start, active = active, createdAt = created, updatedAt = 0)

    @Test fun doseTimesFollowMeals() {
        assertEquals(7 * 60 + 45, s.doseTime(Slot.MORNING, MealTiming.BEFORE))
        assertEquals(8 * 60 + 30, s.doseTime(Slot.MORNING, MealTiming.AFTER))
        assertEquals(21 * 60 + 30, s.doseTime(Slot.NIGHT, MealTiming.AFTER))
    }

    @Test fun groupsShareSlotAndTiming() {
        val g = DoseSchedule.groupsOn("2026-10-29", listOf(med("a", "morning,night"), med("b", "morning"), med("c", "morning", timing = "before")))
        assertEquals(setOf("a", "b"), g[DoseGroup("2026-10-29", Slot.MORNING, MealTiming.AFTER)]!!.map { it.id }.toSet())
        assertEquals(listOf("c"), g[DoseGroup("2026-10-29", Slot.MORNING, MealTiming.BEFORE)]!!.map { it.id })
        assertEquals(3, g.size)
    }

    @Test fun courseEndsAndStoppedHasNoDoses() {
        val course = med("a", "morning", type = "course", days = 5, start = "2026-10-25")
        assertEquals("2026-10-29", course.endDate)
        assertTrue(course.isScheduledOn("2026-10-29"))
        assertFalse(course.isScheduledOn("2026-10-30"))
        assertEquals(MedState.COMPLETED, course.state("2026-10-30"))
        assertFalse(med("b", "morning", active = false).isScheduledOn("2026-10-29"))
    }

    @Test fun groupTimeIsLocalDhakaTime() {
        val g = DoseGroup("2026-10-29", Slot.MORNING, MealTiming.AFTER)
        val t = DoseSchedule.timeOf(g, s)
        assertEquals(8 * 60 + 30, Dates.minuteOfDay(t))
        assertEquals("2026-10-29", Dates.format(t))
        assertEquals(t + 30 * 60_000L, DoseSchedule.deadline(g, s))
        assertEquals(4, Dates.daysBetween("2026-10-25", "2026-10-29"))
    }

    @Test fun overdueSkipsLoggedAndNewlyAddedMedicines() {
        val day = "2026-10-29"
        val base = DoseSchedule.timeOf(DoseGroup(day, Slot.MORNING, MealTiming.AFTER), s)
        val meds = listOf(med("a", "morning"), med("b", "morning"), med("late", "morning", created = base + 1))
        val logs = listOf(DoseLogEntity(DoseLogEntity.idOf(day, "a", Slot.MORNING), "a", day, "morning", "taken", base, base))
        assertEquals(listOf("b"), DoseSchedule.overdue(listOf(day), meds, logs, s, base + 31 * 60_000L).map { it.second.id })
        assertTrue(DoseSchedule.overdue(listOf(day), meds, logs, s, base + 5 * 60_000L).isEmpty())
    }

    @Test fun adherencePercent() {
        val logs = listOf("taken", "taken", "missed", "skipped").mapIndexed { i, st -> DoseLogEntity("$i", "a", "2026-10-29", "morning", st, 0, 0) }
        assertEquals(50, DoseSchedule.adherence(logs, listOf("2026-10-29")))
        assertNull(DoseSchedule.adherence(emptyList(), listOf("2026-10-29")))
    }

    @Test fun groupKeyRoundTrips() {
        val g = DoseGroup("2026-10-29", Slot.NOON, MealTiming.BEFORE)
        assertEquals(g, DoseGroup.parse(g.key))
    }
}
