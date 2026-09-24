package com.ybugmobile.waktiva.data.local.preferences

import com.ybugmobile.waktiva.domain.model.PrayerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSettingsAdhanTest {

    @Test
    fun allPrayersEnabledByDefault() {
        val settings = settings(playAdhanAudio = true)

        listOf(PrayerType.FAJR, PrayerType.DHUHR, PrayerType.ASR, PrayerType.MAGHRIB, PrayerType.ISHA)
            .forEach { assertTrue(it.name, settings.isAdhanEnabledFor(it)) }
    }

    @Test
    fun disabledPrayerIsSilentWhileOthersPlay() {
        val settings = settings(playAdhanAudio = true, disabled = setOf(PrayerType.FAJR))

        assertFalse(settings.isAdhanEnabledFor(PrayerType.FAJR))
        assertTrue(settings.isAdhanEnabledFor(PrayerType.DHUHR))
        assertTrue(settings.isAdhanEnabledFor(PrayerType.ISHA))
    }

    @Test
    fun globalSwitchOverridesPerPrayerSelection() {
        val settings = settings(playAdhanAudio = false)

        assertFalse(settings.isAdhanEnabledFor(PrayerType.DHUHR))
    }

    @Test
    fun sunriseAndUnknownNeverPlay() {
        val settings = settings(playAdhanAudio = true)

        assertFalse(settings.isAdhanEnabledFor(PrayerType.SUNRISE))
        assertFalse(settings.isAdhanEnabledFor(null))
    }

    private fun settings(
        playAdhanAudio: Boolean,
        disabled: Set<PrayerType> = emptySet()
    ) = UserSettings(
        madhab = 0,
        calculationMethod = DEFAULT_CALCULATION_METHOD,
        latitude = null,
        longitude = null,
        altitude = null,
        locationName = "",
        language = "system",
        selectedAdhanPath = null,
        prayerSpecificAdhanPaths = emptyMap(),
        useSpecificAdhanForEachPrayer = false,
        playAdhanAudio = playAdhanAudio,
        isSetupComplete = true,
        enablePreAdhanWarning = true,
        preAdhanWarningMinutes = 5,
        mutedPrayerName = null,
        mutedPrayerDate = null,
        testAlarmEndTime = null,
        fajrAlarmMinutesBeforeSunrise = 45,
        useFajrAlarmBeforeSunrise = false,
        adhanDisabledPrayers = disabled
    )
}
