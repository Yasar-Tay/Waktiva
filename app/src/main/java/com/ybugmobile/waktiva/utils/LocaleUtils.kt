package com.ybugmobile.waktiva.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

fun applyAppLanguage(context: Context, languageTag: String) {
    val locales = languageTag.toAppLocales()
    if (AppCompatDelegate.getApplicationLocales() == locales) {
        return
    }

    AppCompatDelegate.setApplicationLocales(locales)
    context.findActivity()?.recreate()
}

fun String.toAppLocales(): LocaleListCompat {
    return if (this == "system") {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(this)
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
