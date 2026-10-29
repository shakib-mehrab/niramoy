package com.niramoy.i18n

/**
 * Same API as Ad Deen (Strings.t(key, lang)) so the language switches instantly inside the app,
 * but the texts live in one file per language: StringsBn.kt and StringsEn.kt.
 * A unit test (StringsTest) checks both files have the same keys and every key used in code exists.
 */
object Strings {
    fun t(key: String, lang: String): String =
        (if (lang == "en") StringsEn.map[key] else StringsBn.map[key]) ?: StringsEn.map[key] ?: key

    /** Fills {0}, {1}... placeholders. */
    fun f(key: String, lang: String, vararg args: Any): String {
        var s = t(key, lang)
        args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
        return s
    }
}
