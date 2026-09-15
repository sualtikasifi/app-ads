package com.sualtikasifi.cizimhafiza.domain.model

import androidx.annotation.StringRes
import com.sualtikasifi.cizimhafiza.R

/**
 * Every language this app actually ships a word pool and a full UI
 * translation for — the Settings screen's language picker (now a dropdown,
 * not a fixed pair of buttons) is driven entirely off [entries], so adding
 * a language is adding one line here plus its assets/words_XX.json and
 * res/values-XX/strings.xml, not touching the picker's own code.
 *
 * [ENGLISH] is [default] on purpose: it is this app's actual bare resource
 * set (res/values/strings.xml, no locale qualifier — see that file's own
 * note) and assets/words_en.json, so it is what a device whose system
 * language is not [entries] resolves to anyway. Making it the explicit
 * fallback here just keeps this enum's idea of "what's selected" in sync
 * with what the player is actually seeing, rather than naming a language
 * (Turkish, say) the screen was never actually showing them.
 */
enum class SupportedLanguage(val code: String, @StringRes val labelRes: Int) {
    ENGLISH("en", R.string.settings_language_english),
    TURKISH("tr", R.string.settings_language_turkish);

    companion object {
        val default = ENGLISH

        /** [code], or [default] for anything this app does not ship — an unsupported system language, most often. */
        fun resolve(code: String?): SupportedLanguage = entries.firstOrNull { it.code == code } ?: default
    }
}
