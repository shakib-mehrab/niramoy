package com.niramoy.model

/** Time of day a medicine is taken. Each slot is tied to one meal from the routine. */
enum class Slot(val id: String) {
    MORNING("morning"), NOON("noon"), NIGHT("night");

    companion object {
        fun of(id: String?): Slot? = entries.firstOrNull { it.id == id }
        /** "morning,night" -> {MORNING, NIGHT} */
        fun parseSet(s: String): Set<Slot> = s.split(',').mapNotNull { of(it.trim()) }.toSortedSet()
        fun toCsv(set: Set<Slot>): String = set.sorted().joinToString(",") { it.id }
    }
}

enum class MealTiming(val id: String) {
    BEFORE("before"), AFTER("after");

    companion object {
        fun of(id: String?): MealTiming = entries.firstOrNull { it.id == id } ?: AFTER
    }
}

enum class DurationType(val id: String) {
    CONTINUOUS("continuous"), COURSE("course");

    companion object {
        fun of(id: String?): DurationType = entries.firstOrNull { it.id == id } ?: CONTINUOUS
    }
}

enum class DoseStatus(val id: String) {
    TAKEN("taken"), MISSED("missed"), SKIPPED("skipped");

    companion object {
        fun of(id: String?): DoseStatus? = entries.firstOrNull { it.id == id }
    }
}

/** Display state of a medicine. */
enum class MedState { ACTIVE, UPCOMING, STOPPED, COMPLETED }

enum class SugarTag(val id: String) {
    FASTING("fasting"), AFTER_MEAL("after_meal"), RANDOM("random");

    companion object {
        fun of(id: String?): SugarTag? = entries.firstOrNull { it.id == id }
    }
}

enum class RecordKind(val id: String) {
    PRESCRIPTION("prescription"), REPORT("report");

    companion object {
        fun of(id: String?): RecordKind = entries.firstOrNull { it.id == id } ?: PRESCRIPTION
    }
}

/** Reading level used for colouring (always shown with an icon + word, never colour alone). */
enum class Level { LOW, NORMAL, HIGH }

/** Simple display ranges. A reminder tool, not medical advice: these only colour the history. */
object Ranges {
    fun sugar(value: Double, tag: SugarTag?): Level = when {
        value < 70 -> Level.LOW
        tag == SugarTag.FASTING && value > 125 -> Level.HIGH
        tag != SugarTag.FASTING && value >= 180 -> Level.HIGH
        else -> Level.NORMAL
    }

    fun pressure(sys: Int, dia: Int): Level = when {
        sys >= 140 || dia >= 90 -> Level.HIGH
        sys < 90 || dia < 60 -> Level.LOW
        else -> Level.NORMAL
    }

    fun temperature(f: Double): Level = when {
        f >= 99.5 -> Level.HIGH
        f < 95.0 -> Level.LOW
        else -> Level.NORMAL
    }

    fun pulse(p: Int): Level = when {
        p > 100 -> Level.HIGH
        p < 50 -> Level.LOW
        else -> Level.NORMAL
    }
}
