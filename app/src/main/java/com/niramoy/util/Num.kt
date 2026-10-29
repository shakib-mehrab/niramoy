package com.niramoy.util

/** Bangla <-> ASCII digits and language-aware number/time/date formatting. */
object Num {
    private const val BN = "০১২৩৪৫৬৭৮৯"

    fun toBn(s: String): String = buildString { s.forEach { c -> append(if (c in '0'..'9') BN[c - '0'] else c) } }

    fun toAscii(s: String): String = buildString {
        s.forEach { c ->
            val i = BN.indexOf(c)
            append(if (i >= 0) ('0' + i) else c)
        }
    }

    /** Localises every digit in [s] for [lang]. */
    fun loc(s: String, lang: String): String = if (lang == "bn") toBn(s) else s
    fun loc(n: Int, lang: String): String = loc(n.toString(), lang)

    fun loc(d: Double, lang: String): String {
        val s = if (d % 1.0 == 0.0) d.toLong().toString() else String.format(java.util.Locale.US, "%.1f", d)
        return loc(s, lang)
    }

    /** Parses a user-typed number in either script ("১২০", "120", "98.6"). */
    fun parseDouble(s: String): Double? = toAscii(s.trim()).replace(',', '.').toDoubleOrNull()
    fun parseInt(s: String): Int? = toAscii(s.trim()).toIntOrNull()

    /** Bangla: part-of-day word + 12h time ("সকাল ৮:৩০"). English: "8:30 AM". */
    fun time(minuteOfDay: Int, lang: String): String {
        val m = ((minuteOfDay % 1440) + 1440) % 1440
        val h24 = m / 60
        val min = m % 60
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        val hm = "$h12:${min.toString().padStart(2, '0')}"
        return if (lang == "bn") {
            val part = when (h24) {
                in 4..5 -> "ভোর"
                in 6..11 -> "সকাল"
                in 12..14 -> "দুপুর"
                in 15..17 -> "বিকাল"
                in 18..19 -> "সন্ধ্যা"
                else -> "রাত"
            }
            "$part ${toBn(hm)}"
        } else {
            "$hm ${if (h24 < 12) "AM" else "PM"}"
        }
    }

    private val BN_MONTHS = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
    private val EN_MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val BN_DAYS = listOf("রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার")
    private val EN_DAYS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    fun monthName(month0: Int, lang: String) = (if (lang == "bn") BN_MONTHS else EN_MONTHS)[month0.coerceIn(0, 11)]
    fun dayName(calendarDow: Int, lang: String) = (if (lang == "bn") BN_DAYS else EN_DAYS)[(calendarDow - 1).coerceIn(0, 6)]
    fun shortDayName(calendarDow: Int, lang: String) = dayName(calendarDow, lang).let { if (lang == "bn") it.removeSuffix("বার") else it.take(3) }

    /** "২৯ অক্টোবর ২০২৬" / "29 October 2026". */
    fun date(date: String, lang: String, withYear: Boolean = true): String {
        val d = Dates.dayOfMonth(date)
        val m = monthName(Dates.month(date), lang)
        val y = Dates.year(date)
        val s = if (withYear) "$d $m $y" else "$d $m"
        return loc(s, lang)
    }
}
