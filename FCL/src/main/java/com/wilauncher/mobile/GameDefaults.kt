package com.wilauncher.mobile

import android.content.Context

/** Defaults WiLauncher applies to a fresh game directory (see assets/options.txt). */
object GameDefaults {

    /** Region Minecraft uses for a language when the device locale has none (or an unusual one). */
    private val DEFAULT_REGION = mapOf(
        "ru" to "ru", "uk" to "ua", "be" to "by", "kk" to "kz", "en" to "us", "de" to "de",
        "fr" to "fr", "es" to "es", "pt" to "br", "it" to "it", "pl" to "pl", "tr" to "tr",
        "ja" to "jp", "ko" to "kr", "zh" to "cn", "vi" to "vn", "id" to "id", "fa" to "ir",
        "ar" to "sa", "cs" to "cz", "nl" to "nl", "sv" to "se", "fi" to "fi", "hu" to "hu",
        "ro" to "ro", "bg" to "bg", "el" to "gr", "he" to "il", "th" to "th", "uz" to "uz",
        "az" to "az", "hy" to "am", "ka" to "ge", "lt" to "lt", "lv" to "lv", "et" to "ee",
        "sr" to "sp", "hr" to "hr", "sk" to "sk", "sl" to "si", "da" to "dk", "nb" to "no",
    )

    /** Minecraft language code (e.g. "ru_ru") matching the launcher's interface language. */
    @JvmStatic
    fun languageCode(context: Context): String {
        val locale = context.resources.configuration.locales[0]
        val language = locale.language.lowercase()
        if (language == "zh") {
            return if (locale.country.uppercase() in setOf("TW", "HK", "MO")) "zh_tw" else "zh_cn"
        }
        val region = DEFAULT_REGION[language] ?: return "en_us"
        return "${language}_$region"
    }
}
