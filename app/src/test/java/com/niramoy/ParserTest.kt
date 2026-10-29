package com.niramoy

import com.niramoy.model.DurationType
import com.niramoy.model.MealTiming
import com.niramoy.model.Slot
import com.niramoy.model.SugarTag
import com.niramoy.parse.PrescriptionParser
import com.niramoy.parse.VoiceParser
import com.niramoy.util.Num
import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    @Test fun voiceExampleFromSpec() {
        val r = VoiceParser.parse("আজ শরীরটা ভালো না, সুগার ১২০, প্রেশার ১৩০ বাই ৮০")
        assertEquals(2, r.mood)
        assertEquals(120.0, r.sugar!!, 0.01)
        assertEquals(130, r.systolic)
        assertEquals(80, r.diastolic)
    }

    @Test fun voiceSlashTagsAndMmol() {
        val r = VoiceParser.parse("খালি পেটে সুগার 7.0, বিপি 140/90, পালস 78, ওজন 60 কেজি, জ্বর 101")
        assertEquals(126.0, r.sugar!!, 0.5)
        assertEquals(SugarTag.FASTING, r.sugarTag)
        assertEquals(140, r.systolic); assertEquals(90, r.diastolic)
        assertEquals(78, r.pulse)
        assertEquals(60.0, r.weight!!, 0.01)
        assertEquals(101.0, r.temperature!!, 0.01)
    }

    @Test fun voiceEnglishAndMood() {
        val r = VoiceParser.parse("feeling very good, sugar 140 after meal, pressure 120 by 80")
        assertEquals(5, r.mood)
        assertEquals(SugarTag.AFTER_MEAL, r.sugarTag)
        assertEquals(120, r.systolic)
        assertEquals(3, VoiceParser.parse("মোটামুটি আছি").mood)
    }

    @Test fun prescriptionLines() {
        val text = listOf(
            "Rx",
            "1. Tab. Napa 500mg   1+0+1  খাবারের পরে  ৭ দিন",
            "2. Cap. Omep 20 mg",
            "   ১+০+১ (খাবারের আগে) --- চলবে",
            "3. Syp. Ambrox 1 tsf x 3 times, 5 days"
        ).joinToString("\n")
        val d = PrescriptionParser.parse(text, "en")
        assertEquals(3, d.size)
        assertTrue(d[0].name, d[0].name.startsWith("Napa"))
        assertEquals(setOf(Slot.MORNING, Slot.NIGHT), d[0].slots)
        assertEquals(MealTiming.AFTER, d[0].timing)
        assertEquals(DurationType.COURSE, d[0].durationType); assertEquals(7, d[0].courseDays)
        assertEquals(MealTiming.BEFORE, d[1].timing)
        assertEquals(DurationType.CONTINUOUS, d[1].durationType)
        assertEquals(setOf(Slot.MORNING, Slot.NOON, Slot.NIGHT), d[2].slots)
        assertEquals("5 ml", d[2].dose)
        assertEquals("১ ট্যাবলেট", PrescriptionParser.localiseDose("1 tablet", "bn"))
    }

    @Test fun banglaNumbersAndTime() {
        assertEquals("১২০", Num.toBn("120"))
        assertEquals("120", Num.toAscii("১২০"))
        assertEquals(98.6, Num.parseDouble("৯৮.৬")!!, 0.001)
        assertEquals("সকাল ৮:৩০", Num.time(8 * 60 + 30, "bn"))
        assertEquals("রাত ৯:০৫", Num.time(21 * 60 + 5, "bn"))
        assertEquals("1:30 PM", Num.time(13 * 60 + 30, "en"))
    }
}
