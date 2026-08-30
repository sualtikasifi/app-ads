package com.sualtikasifi.cizimhafiza.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A user-facing message a ViewModel can produce without reaching for a
 * `Context` — resolved to real text only at the point it is drawn.
 *
 * The app ships a complete `values-en/strings.xml`, but error messages used
 * to be built as literal Turkish strings inside ViewModels and repositories
 * ("Bu kodla bir oda bulunamadı", "Kaydedilemedi, tekrar dene", …). Those
 * bypass resource resolution entirely, so a device running the app in
 * English got a fully English UI right up until something went wrong, then
 * a Turkish sentence. Wrapping the id instead of the text keeps the
 * ViewModel free of Android framework dependencies while letting the string
 * go through the normal locale lookup.
 */
sealed interface UiText {

    /** A localized string resource, with optional format arguments. */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /**
     * Text that genuinely has no resource — server-supplied content such as a
     * developer's reply to a bug report. Never use this for app-authored
     * copy; that always belongs in strings.xml.
     */
    data class Raw(val value: String) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
    }
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> if (args.isEmpty()) {
        stringResource(id)
    } else {
        stringResource(id, *args.toTypedArray())
    }
}
