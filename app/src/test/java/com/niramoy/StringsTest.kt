package com.niramoy

import com.niramoy.i18n.StringsBn
import com.niramoy.i18n.StringsEn
import com.niramoy.model.MealTiming
import com.niramoy.model.Slot
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** No hard-coded UI text: every key used in code exists in both languages, and both files match. */
class StringsTest {

    @Test fun bothLanguagesHaveSameKeys() {
        val onlyBn = StringsBn.map.keys - StringsEn.map.keys
        val onlyEn = StringsEn.map.keys - StringsBn.map.keys
        assertTrue("only in bn: $onlyBn", onlyBn.isEmpty())
        assertTrue("only in en: $onlyEn", onlyEn.isEmpty())
    }

    @Test fun everyKeyUsedInCodeExists() {
        val src = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" && !it.path.contains("i18n") }
            .joinToString("\n") { it.readText() }
        // t("key"), tf("key", ..), Strings.t("key", ..), Strings.f("key", ..) and t(if (..) "a" else "b")
        val call = Regex("""\b(?:t|tf|Strings\.t|Strings\.f)\((?:if \([^)]*\) )?"([a-z0-9_]+)"(?: else "([a-z0-9_]+)")?""")
        val errCodes = Regex("\"(err_[a-z_]+)\"")
        val permKeys = Regex("PermStep\\(\"([a-z_]+)\"")
        val used = mutableSetOf<String>()
        call.findAll(src).forEach { m -> m.groupValues.drop(1).filter { it.isNotBlank() }.forEach { used += it } }
        errCodes.findAll(src).forEach { used += it.groupValues[1] }
        permKeys.findAll(src).forEach { used += it.groupValues[1]; used += it.groupValues[1] + "_why" }
        // Keys built at runtime.
        Slot.entries.forEach { s ->
            used += "slot_${s.id}"
            MealTiming.entries.forEach { m -> used += "phrase_${s.id}_${m.id}"; used += "when_${s.id}_${m.id}" }
        }
        MealTiming.entries.forEach { used += "timing_${it.id}" }
        (1..5).forEach { used += "mood_$it" }
        listOf("fasting", "after_meal", "random").forEach { used += "tag_$it" }
        listOf("voice", "generic", "beep", "system").forEach { used += "tone_$it" }
        listOf("wake", "breakfast", "lunch", "dinner", "sleep").forEach { used += "rt_$it" }
        listOf("ob_language", "ob_name", "ob_wake", "ob_breakfast", "ob_lunch", "ob_dinner", "ob_sleep", "ob_blood",
            "st_taken", "st_missed", "st_skipped", "st_waiting", "state_active", "state_upcoming", "state_stopped",
            "state_completed", "lvl_high", "lvl_low", "lvl_normal", "tab_home").forEach { used += it }
        val missing = used.filter { it !in StringsEn.map || it !in StringsBn.map }.sorted()
        assertTrue("missing keys: $missing", missing.isEmpty())
    }
}
