package com.ybugmobile.vaktiva.domain.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ybugmobile.vaktiva.R

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

    companion object {
        fun fromString(name: String): PrayerType? = entries.find { it.name.equals(name, ignoreCase = true) }
    }
}