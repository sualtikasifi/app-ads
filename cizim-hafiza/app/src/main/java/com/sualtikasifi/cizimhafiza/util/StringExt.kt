package com.sualtikasifi.cizimhafiza.util

import java.util.Locale

private val TR_LOCALE = Locale.forLanguageTag("tr-TR")

/**
 * Capitalizes only the first character, locale-aware — Turkish words need
 * `Locale("tr")` for a correct dotted "İ" ("istatistik" → "İstatistik"),
 * while English words must NOT use Turkish rules (that would wrongly turn
 * "ice cream" into "İce cream"). Pass the word pool's current language
 * ("tr"/"en" — see WordSeeder.currentLanguage), not the device's raw
 * system locale, since the two can differ.
 */
fun String.capitalizeForWordLanguage(language: String): String {
    if (isEmpty()) return this
    val locale = if (language == "tr") TR_LOCALE else Locale.ROOT
    return this[0].toString().uppercase(locale) + substring(1)
}
