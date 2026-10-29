package com.niramoy.parse

import com.niramoy.model.SugarTag
import com.niramoy.util.Num

/** What we could understand from one spoken sentence. Everything is shown for confirmation before saving. */
data class VoiceResult(
    val mood: Int? = null,
    val sugar: Double? = null,
    val sugarTag: SugarTag? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val pulse: Int? = null,
    val weight: Double? = null,
    val temperature: Double? = null,
    val note: String = ""
) {
    val isEmpty get() = mood == null && sugar == null && systolic == null && pulse == null && weight == null && temperature == null
}

/**
 * Rule-based parser for spoken Bangla/English health notes, e.g.
 * "আজ শরীরটা ভালো না, সুগার ১২০, প্রেশার ১৩০ বাই ৮০".
 * Rules are predictable and work offline; anything not understood stays in the note.
 */
object VoiceParser {

    private val SUGAR = listOf("সুগার", "সুগারের", "শুগার", "ডায়াবেটিস", "ডায়াবেটিস", "গ্লুকোজ", "sugar", "glucose", "diabetes")
    private val PRESSURE = listOf("প্রেশার", "প্রেসার", "প্রেশারের", "প্রেসারের", "বিপি", "রক্তচাপ", "pressure", "bp", "b.p")
    private val PULSE = listOf("পালস", "নাড়ি", "হার্টবিট", "pulse", "heart rate")
    private val WEIGHT = listOf("ওজন", "weight", "কেজি", "kg")
    private val TEMP = listOf("জ্বর", "তাপমাত্রা", "টেম্পারেচার", "temperature", "fever", "temp")
    private val FASTING = listOf("খালি পেটে", "খালিপেটে", "সকালে খালি", "fasting", "empty stomach")
    private val AFTER_MEAL = listOf("খাওয়ার পরে", "খাওয়ার পর", "খাবারের পরে", "খাবার পরে", "খাবারের পর", "after meal", "after food", "after eating")

    /** Number words recognisers sometimes return instead of digits. */
    private val BN_WORDS = mapOf(
        "এক" to 1, "দুই" to 2, "তিন" to 3, "চার" to 4, "পাঁচ" to 5, "ছয়" to 6, "সাত" to 7, "আট" to 8, "নয়" to 9, "দশ" to 10,
        "এগারো" to 11, "বারো" to 12, "তেরো" to 13, "চৌদ্দ" to 14, "পনেরো" to 15, "ষোল" to 16, "সতেরো" to 17, "আঠারো" to 18, "উনিশ" to 19,
        "বিশ" to 20, "ত্রিশ" to 30, "চল্লিশ" to 40, "পঞ্চাশ" to 50, "ষাট" to 60, "সত্তর" to 70, "আশি" to 80, "নব্বই" to 90, "একশো" to 100, "একশ" to 100
    )

    private val NUM = Regex("""\d+(?:\.\d+)?""")

    fun normalise(raw: String): String {
        var s = Num.toAscii(raw.lowercase())
        // "দেড়শো" style words are rare from the recogniser; handle the common simple ones.
        BN_WORDS.entries.sortedByDescending { it.key.length }.forEach { (w, n) -> s = s.replace(Regex("(?<![\\p{L}])$w(?![\\p{L}])"), n.toString()) }
        return s.replace('।', ',')
    }

    /** First number appearing after the first matching keyword (within ~25 chars). */
    private fun numberAfter(s: String, keys: List<String>): Pair<Double, Int>? {
        for (k in keys) {
            var idx = s.indexOf(k)
            while (idx >= 0) {
                val tail = s.substring(idx + k.length).take(25)
                NUM.find(tail)?.let { return it.value.toDouble() to (idx + k.length + it.range.first) }
                idx = s.indexOf(k, idx + 1)
            }
        }
        return null
    }

    fun parse(raw: String): VoiceResult {
        val s = normalise(raw)
        var r = VoiceResult(note = raw.trim())

        // Blood pressure: "130 বাই 80", "130/80", "130 by 80", "130 - 80", or two numbers after the keyword.
        val bpPair = Regex("""(\d{2,3})\s*(?:/|বাই|by|বা|-|ও|and)\s*(\d{2,3})""")
        val pIdx = PRESSURE.map { s.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val bpMatch = if (pIdx != null) bpPair.find(s, pIdx) else bpPair.find(s)?.takeIf { it.value.contains('/') }
        bpMatch?.let {
            val a = it.groupValues[1].toInt(); val b = it.groupValues[2].toInt()
            if (a in 60..260 && b in 30..160 && a > b) r = r.copy(systolic = a, diastolic = b)
        }

        numberAfter(s, SUGAR)?.let { (v, _) ->
            // Values like 7.8 are mmol/L (common on BD glucometers) -> mg/dL.
            val mg = if (v < 35) v * 18.0 else v
            if (mg in 20.0..700.0) r = r.copy(sugar = Math.round(mg).toDouble())
        }
        if (r.sugar != null) r = r.copy(sugarTag = when {
            FASTING.any { s.contains(it) } -> SugarTag.FASTING
            AFTER_MEAL.any { s.contains(it) } -> SugarTag.AFTER_MEAL
            else -> null
        })

        numberAfter(s, PULSE)?.let { (v, _) -> if (v in 30.0..220.0) r = r.copy(pulse = v.toInt()) }
        numberAfter(s, WEIGHT)?.let { (v, _) -> if (v in 20.0..250.0) r = r.copy(weight = v) }
            ?: Regex("""(\d{2,3}(?:\.\d)?)\s*(?:কেজি|kg)""").find(s)?.let { r = r.copy(weight = it.groupValues[1].toDouble()) }
        numberAfter(s, TEMP)?.let { (v, _) ->
            val f = if (v in 34.0..43.0) v * 9 / 5 + 32 else v // Celsius -> Fahrenheit
            if (f in 90.0..110.0) r = r.copy(temperature = Math.round(f * 10) / 10.0)
        }

        r = r.copy(mood = mood(s))
        return r
    }

    /** 1 very bad .. 5 very good. Negations are checked before plain "ভালো". */
    fun mood(s: String): Int? = when {
        listOf("খুব খারাপ", "অনেক খারাপ", "very bad", "terrible").any { s.contains(it) } -> 1
        listOf("ভালো না", "ভাল না", "ভালো লাগছে না", "ভাল লাগছে না", "খারাপ", "অসুস্থ", "not good", "not well", "bad", "sick").any { s.contains(it) } -> 2
        listOf("মোটামুটি", "চলছে", "ঠিক আছে", "okay", "ok", "so so").any { s.contains(it) } -> 3
        listOf("খুব ভালো", "খুব ভাল", "অনেক ভালো", "অনেক ভাল", "very good", "great", "excellent").any { s.contains(it) } -> 5
        listOf("ভালো", "ভাল", "good", "fine", "well").any { s.contains(it) } -> 4
        else -> null
    }
}
