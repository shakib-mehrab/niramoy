package com.niramoy.parse

import com.niramoy.model.DurationType
import com.niramoy.model.MealTiming
import com.niramoy.model.Slot
import com.niramoy.util.Num

/** An editable medicine row (manual add, bulk add and OCR review all use this). */
data class MedicineDraft(
    val name: String = "",
    val dose: String = "",
    val slots: Set<Slot> = setOf(Slot.MORNING),
    val timing: MealTiming = MealTiming.AFTER,
    val durationType: DurationType = DurationType.CONTINUOUS,
    val courseDays: Int = 7,
    val notes: String = ""
)

/**
 * Turns OCR text of a Bangladeshi prescription into drafts. Typical lines:
 *   "Tab. Napa 500mg      1+0+1  খাবারের পরে  ৭ দিন"
 *   "Cap. Omep 20 mg  ১+০+১ (খাবারের আগে) --- চলবে"
 *   "Syp. Ambrox 1 tsf x 3 times, 5 days"
 * Handwriting OCR is imperfect, so this only pre-fills the review screen; the user confirms everything.
 */
object PrescriptionParser {

    private val FORM = Regex("""^\s*(?:\d+[.)]\s*)?(tab|tabs|tablet|cap|caps|capsule|syp|syr|syrup|susp|inj|drop|drops|cream|oint|sach|সিরাপ|ট্যাব|ক্যাপ)\b\.?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val PATTERN3 = Regex("""([0-9½]+(?:\.5)?)\s*[+\-]\s*([0-9½]+(?:\.5)?)\s*[+\-]\s*([0-9½]+(?:\.5)?)""")
    private val DAYS = Regex("""(\d+)\s*(দিন|days?|d\b|সপ্তাহ|weeks?|মাস|months?)""", RegexOption.IGNORE_CASE)
    private val STRENGTH = Regex("""\d+(?:\.\d+)?\s*(?:mg|mcg|gm|g|ml|iu)\b""", RegexOption.IGNORE_CASE)

    fun parse(raw: String, lang: String = "bn"): List<MedicineDraft> {
        val lines = Num.toAscii(raw).lines().map { it.trim() }.filter { it.isNotBlank() }
        val out = mutableListOf<MedicineDraft>()
        var i = 0
        while (i < lines.size) {
            val m = FORM.find(lines[i])
            if (m == null) { i++; continue }
            val form = m.groupValues[1].lowercase()
            // Instructions are often on the next line; read until the next medicine.
            val block = StringBuilder(lines[i])
            var j = i + 1
            while (j < lines.size && FORM.find(lines[j]) == null && j <= i + 2) { block.append(' ').append(lines[j]); j++ }
            out += draftFrom(form, m.groupValues[2], block.toString()).let { d -> d.copy(dose = localiseDose(d.dose, lang)) }
            i = j
        }
        return out
    }

    private val BN_UNITS = mapOf("tablet" to "ট্যাবলেট", "capsule" to "ক্যাপসুল", "ml" to "মিলি", "drop" to "ফোঁটা")

    /** "1 tablet" -> "১ ট্যাবলেট" in Bangla mode. */
    fun localiseDose(dose: String, lang: String): String {
        if (lang != "bn") return dose
        var d = dose
        BN_UNITS.forEach { (en, bn) -> d = d.replace(en, bn) }
        return Num.toBn(d)
    }

    private fun draftFrom(form: String, rest: String, block: String): MedicineDraft {
        val lower = block.lowercase()
        // Name = words before the first strength / dosing pattern.
        val cut = listOfNotNull(PATTERN3.find(rest)?.range?.first, Regex("""\d""").find(rest)?.range?.first?.let { idx ->
            STRENGTH.find(rest)?.range?.last?.plus(1) ?: idx
        }).minOrNull() ?: rest.length
        val name = rest.substring(0, cut.coerceAtMost(rest.length)).trim().trimEnd('-', ',', '.', ':')
            .split(' ').take(3).joinToString(" ").replaceFirstChar { it.uppercase() }
        val strength = STRENGTH.find(rest)?.value?.replace(" ", "").orEmpty()
        val fullName = listOf(name, strength).filter { it.isNotBlank() }.joinToString(" ")

        var slots = setOf(Slot.MORNING)
        var amount = ""
        PATTERN3.find(block)?.let { p ->
            val parts = (1..3).map { p.groupValues[it] }
            val s = mutableSetOf<Slot>()
            if (parts[0] != "0") s += Slot.MORNING
            if (parts[1] != "0") s += Slot.NOON
            if (parts[2] != "0") s += Slot.NIGHT
            if (s.isNotEmpty()) slots = s
            amount = parts.firstOrNull { it != "0" }.orEmpty()
        } ?: run {
            when {
                Regex("""(x|×)\s*3|3\s*(times|বার)|tds|tid""").containsMatchIn(lower) -> slots = setOf(Slot.MORNING, Slot.NOON, Slot.NIGHT)
                Regex("""(x|×)\s*2|2\s*(times|বার)|bd\b|bid""").containsMatchIn(lower) -> slots = setOf(Slot.MORNING, Slot.NIGHT)
                Regex("""\bhs\b|রাতে|at night""").containsMatchIn(lower) -> slots = setOf(Slot.NIGHT)
            }
        }

        val timing = when {
            listOf("আগে", "before", "a.c", " ac ", "খালি পেটে").any { lower.contains(it) } -> MealTiming.BEFORE
            else -> MealTiming.AFTER
        }

        val unit = when {
            form.startsWith("cap") || form == "ক্যাপ" -> "capsule"
            form.startsWith("sy") || form.startsWith("susp") || form == "সিরাপ" -> "ml"
            form.startsWith("drop") -> "drop"
            else -> "tablet"
        }
        val dose = when {
            unit == "ml" -> Regex("""(\d+)\s*(?:ml|tsf|চামচ)""", RegexOption.IGNORE_CASE).find(lower)?.let { mm ->
                val n = mm.groupValues[1].toInt()
                if (mm.value.contains("tsf") || mm.value.contains("চামচ")) "${n * 5} ml" else "$n ml"
            } ?: "5 ml"
            amount == "½" || amount == "0.5" -> "½ $unit"
            amount.isNotBlank() -> "$amount $unit"
            else -> "1 $unit"
        }

        val continuous = listOf("চলবে", "continue", "cont.", "চালিয়ে", "নিয়মিত", "regular").any { lower.contains(it) }
        val days = DAYS.find(lower)?.let { d ->
            val n = d.groupValues[1].toInt()
            when {
                d.groupValues[2].startsWith("সপ্তাহ") || d.groupValues[2].startsWith("week") -> n * 7
                d.groupValues[2].startsWith("মাস") || d.groupValues[2].startsWith("month") -> n * 30
                else -> n
            }
        }
        return MedicineDraft(
            name = fullName,
            dose = dose,
            slots = slots,
            timing = timing,
            durationType = if (!continuous && days != null) DurationType.COURSE else DurationType.CONTINUOUS,
            courseDays = days ?: 7
        )
    }
}
