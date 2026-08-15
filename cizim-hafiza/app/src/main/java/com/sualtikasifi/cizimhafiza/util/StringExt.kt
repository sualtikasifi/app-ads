package com.sualtikasifi.cizimhafiza.util

import java.util.Locale

private val TR_LOCALE = Locale.forLanguageTag("tr-TR")

/**
 * Capitalizes only the first character, Turkish-locale aware (so "istatistik"
 * becomes "İstatistik" with a dotted İ, not the default-locale "Istatistik").
 * Used to display word-pool entries consistently regardless of how they're
 * cased in words.json.
 */
fun String.capitalizeTr(): String {
    if (isEmpty()) return this
    return this[0].toString().uppercase(TR_LOCALE) + substring(1)
}
