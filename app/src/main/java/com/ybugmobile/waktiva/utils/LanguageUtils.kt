package com.ybugmobile.waktiva.utils

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import com.ybugmobile.waktiva.R
import java.util.Locale as JavaLocale

/**
 * Utility object for handling application-wide language and locale settings.
 *
 * This object manages the list of supported languages and provides helper methods
 * to retrieve native language names and display options for the settings UI.
 */
object LanguageUtils {
    /**
     * List of ISO 639-1 language codes currently supported by the application.
     * "system" is a special token representing the device's default locale.
     */
    val supportedLanguages = listOf(
        "system",
        "en", // English
        "tr", // Turkish
        "fr", // French
        "de", // German
        "es", // Spanish
        "it", // Italian
        "ar", // Arabic
        "in", // Indonesian
        "ur", // Urdu
        "bn", // Bengali
        "fa", // Persian
        "ms", // Malay
        "ru", // Russian
        "bs", // Bosnian
        "fi", // Finnish
        "nl", // Dutch
        "pl", // Polish
        "pt", // Portuguese
        "sq", // Albanian
        "sv"  // Swedish
    )

    /**
     * Retrieves the native name of a language (e.g., "Français" for "fr").
     *
     * @param languageCode The ISO 639-1 code of the language, or "system".
     * @return A formatted string representing the language name.
     */
    @Composable
    fun getNativeLanguageName(languageCode: String): String {
        return when (languageCode) {
            "system" -> {
                val systemLocale = ConfigurationCompat.getLocales(Resources.getSystem().configuration).get(0)
                val displayName = systemLocale?.getDisplayName(systemLocale)?.replaceFirstChar { it.uppercase() } ?: ""
                val systemLabel = stringResource(R.string.lang_system)
                if (displayName.isNotEmpty()) "$systemLabel ($displayName)" else systemLabel
            }
            else -> {
                val locale = JavaLocale(languageCode)
                locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
            }
        }
    }

    /**
     * Generates a list of pairs for use in a language selection UI.
     * Each pair consists of the (Display Name, Language Code).
     *
     * @return A list of language options.
     */
    @Composable
    fun getLanguageOptions(): List<Pair<String, String>> {
        val options = supportedLanguages.map { code ->
            getNativeLanguageName(code) to code
        }
        val systemOption = options.find { it.second == "system" }
        val rest = options.filter { it.second != "system" }.sortedBy { it.first }

        return if (systemOption != null) listOf(systemOption) + rest else rest
    }
}
