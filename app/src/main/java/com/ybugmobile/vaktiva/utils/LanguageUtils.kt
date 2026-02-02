package com.ybugmobile.vaktiva.utils

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import com.ybugmobile.vaktiva.R
import java.util.Locale as JavaLocale

object LanguageUtils {
    val supportedLanguages = listOf(
        "system",
        "en",
        "tr",
        "fr",
        "de",
        "es",
        "it",
        "ar",
        "in",
        "ur",
        "bn",
        "fa",
        "ms",
        "ru"
    )

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

    @Composable
    fun getLanguageOptions(): List<Pair<String, String>> {
        return supportedLanguages.map { code ->
            getNativeLanguageName(code) to code
        }
    }
}
