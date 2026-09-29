package com.ybugmobile.waktiva.ui.home.composables

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerType

/** Each prayer's colour, as the day circle paints its ring and badges. */
val PrayerType.accentColor: Color
    get() = when (this) {
        PrayerType.FAJR -> Color(0xFF81D4FA)
        PrayerType.SUNRISE -> Color(0xFFFFE082)
        PrayerType.DHUHR -> Color(0xFFFFF59D)
        PrayerType.ASR -> Color(0xFFFFCC80)
        PrayerType.MAGHRIB -> Color(0xFFCE93D8)
        PrayerType.ISHA -> Color(0xFF9FA8DA)
    }

/** Each prayer's icon on the day circle's badges. */
@get:DrawableRes
val PrayerType.iconRes: Int
    get() = when (this) {
        PrayerType.FAJR -> R.drawable.haze_day_rotated
        PrayerType.SUNRISE -> R.drawable.sunrise
        PrayerType.DHUHR, PrayerType.ASR -> R.drawable.clear_day
        PrayerType.MAGHRIB -> R.drawable.sunset
        PrayerType.ISHA -> R.drawable.clear_night
    }

/** The gold a prayer marked as prayed glows in, on the day circle and in the prayer log. */
val PrayedGold = Color(0xFFFFD54F)

/** The green of the seal on a prayer marked as prayed. */
val PrayedSeal = Color(0xFF10B981)

/** The red of a prayer whose time went without being marked. */
val MissedRed = Color(0xFFF87171)
