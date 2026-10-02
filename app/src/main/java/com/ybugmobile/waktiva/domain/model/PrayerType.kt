package com.ybugmobile.waktiva.domain.model

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ybugmobile.waktiva.R

enum class PrayerType {
    FAJR,
    SUNRISE,
    DHUHR,
    ASR,
    MAGHRIB,
    ISHA;

    val displayName: String
        @Composable
        get() = when (this) {
            FAJR -> stringResource(id = R.string.prayer_fajr)
            SUNRISE -> stringResource(id = R.string.prayer_sunrise)
            DHUHR -> stringResource(id = R.string.prayer_dhuhr)
            ASR -> stringResource(id = R.string.prayer_asr)
            MAGHRIB -> stringResource(id = R.string.prayer_maghrib)
            ISHA -> stringResource(id = R.string.prayer_isha)
        }

    fun getDisplayName(context: Context): String {
        val resId = when (this) {
            FAJR -> R.string.prayer_fajr
            SUNRISE -> R.string.prayer_sunrise
            DHUHR -> R.string.prayer_dhuhr
            ASR -> R.string.prayer_asr
            MAGHRIB -> R.string.prayer_maghrib
            ISHA -> R.string.prayer_isha
        }
        return context.getString(resId)
    }

    /**
     * The prayer itself, as opposed to the time window it is prayed in: the window before sunrise
     * is "İmsak" in Turkish, while the prayer (and its adhan) is "Sabah". Identical to [displayName]
     * for every other prayer and most other languages.
     */
    val prayerName: String
        @Composable
        get() = when (this) {
            FAJR -> stringResource(id = R.string.prayer_name_fajr)
            else -> displayName
        }

    fun getPrayerName(context: Context): String =
        if (this == FAJR) context.getString(R.string.prayer_name_fajr) else getDisplayName(context)

    companion object {
        fun fromString(name: String): PrayerType? = entries.find { it.name.equals(name, ignoreCase = true) }
    }
}