package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.ybugmobile.waktiva.data.local.preferences.UserSettings
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.model.WeatherCondition
import com.ybugmobile.waktiva.ui.home.HomeViewState
import com.ybugmobile.waktiva.ui.theme.GlassTheme
import com.ybugmobile.waktiva.ui.theme.liquidGlass
import java.time.LocalDate
import java.time.LocalTime

/**
 * Landscape-optimized layout for the Home screen.
 * Reorganizes the UI into a more horizontal flow, placing the central visualization
 * alongside the countdown timer to better utilize wide screen space.
 */
@Composable
fun HomeLandscapeContent(
    state: HomeViewState,
    settings: UserSettings?,
    allDays: List<com.ybugmobile.waktiva.domain.model.PrayerDay>,
    calculationMethods: List<Pair<Int, Int>>,
    glassTheme: GlassTheme,
    scrollState: ScrollState,
    localTime: LocalTime,
    contentColor: Color,
    onStatusClick: () -> Unit,
    onToggleCalendarType: (Boolean) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onSkipNextAudio: (String, LocalDate) -> Unit,
    onStopAdhan: () -> Unit,
    onStopTest: () -> Unit,
    onResetDate: () -> Unit,
    onMethodClick: () -> Unit,
    onShowToast: (String) -> Unit,
    /** Screen angle of the sunlight on the day circle's metal (see DayCircle), or null for the default. */
    sunLight: Float? = null,
    /** Today's prayers marked in the prayer log, or null until it has loaded. */
    prayedToday: Set<PrayerType>? = null,
    onLogPrayer: ((PrayerType) -> Unit)? = null
) {
    // The selected day's weather for the day circle; the sky inside it follows the weather effects setting.
    val circleWeather = remember(state.dayForecast, state.weatherCondition, state.weatherEffectCondition, settings?.showWeatherEffects) {
        DayCircleWeather(
            forecast = state.dayForecast,
            nowCondition = state.weatherCondition,
            nowEffect = state.weatherEffectCondition,
            effectsOn = settings?.showWeatherEffects == true
        )
    }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val hasWeatherData = remember(state.temperature, state.weatherCondition) {
        state.temperature != null || state.weatherCondition != WeatherCondition.UNKNOWN
    }
    val availableDays = remember(allDays) { allDays.filter { !it.date.isBefore(LocalDate.now()) } }
    // Captures only the flag, not the state that changes every second, so the day circle can
    // skip the seconds.
    val isHijriSelected = state.isHijriSelected
    val onToggleHijri = remember(isHijriSelected, onToggleCalendarType) { { onToggleCalendarType(!isHijriSelected) } }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 76.dp, end = 24.dp), // Base layout padding (60dp rail + 16dp margin)
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Add status bar padding inside the scrollable area
            Spacer(Modifier.statusBarsPadding())

            // Main Layout: Left Half (Visualization) | Right Half (Header + Countdown)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT HALF: Circular Visualization, as large as the half and the screen height allow
                BoxWithConstraints(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val circleSize = minOf(maxWidth, (screenHeightDp * 0.85f).dp)
                    Box(
                        modifier = Modifier.size(circleSize),
                        contentAlignment = Alignment.Center
                    ) {
                        state.currentPrayerDay?.let { prayerDay ->
                            DayCircle(
                                style = settings?.dayCircleStyle ?: DayCircleStyle.DEFAULT,
                                day = prayerDay,
                                currentTime = if (state.selectedDate == LocalDate.now()) localTime else LocalTime.MIDNIGHT,
                                currentPrayer = if (state.selectedDate == LocalDate.now()) state.currentPrayer else null,
                                isSelectedDayToday = state.selectedDate == LocalDate.now(),
                                isHijriVisible = state.isHijriSelected,
                                onToggleHijri = onToggleHijri,
                                contentColor = contentColor,
                                sunLight = sunLight,
                                prayedPrayers = prayedToday,
                                onLogPrayer = onLogPrayer,
                                weather = circleWeather
                            )
                        }
                    }
                }

                // RIGHT HALF: Header Section + Next Prayer Countdown
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Unified Header Row (Inside Right Half)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Part: Location
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            LocationSection(
                                locationName = state.locationName,
                                contentColor = contentColor,
                                isNetworkAvailable = state.isNetworkAvailable,
                                isLocationEnabled = state.isLocationEnabled,
                                isLocationPermissionGranted = state.isLocationPermissionGranted,
                                onStatusClick = onStatusClick
                            )
                        }

                        // Middle Part: Moon Phase
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            MoonPhaseView(
                                moonPhase = state.moonPhase,
                                contentColor = contentColor,
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = 0.7f
                                        scaleY = 0.7f
                                    }
                            )
                        }

                        // Right Part: Weather
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (hasWeatherData || state.isNetworkAvailable || state.isLocationPermissionGranted) {
                                WeatherSection(
                                    temperature = state.temperature,
                                    condition = state.weatherEffectCondition,
                                    contentColor = contentColor,
                                    currentTime = localTime,
                                    currentPrayerDay = state.currentPrayerDay,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = 0.85f
                                        scaleY = 0.85f
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Adhan Controls or Next Prayer Countdown
                    AnimatedContent(
                        targetState = state.isAdhanPlaying,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "NextPrayerOrAdhan"
                    ) { playing ->
                        if (playing) {
                            AdhanControls(
                                isAdhanPlaying = true,
                                playingPrayerName = state.playingPrayerName,
                                isTest = state.nextPrayer?.isTest == true,
                                onStopAdhan = onStopAdhan,
                                onStopTest = onStopTest,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            NextPrayerCountdown(
                                nextPrayer = state.nextPrayer,
                                selectedDate = state.selectedDate,
                                contentColor = contentColor,
                                currentPrayer = state.currentPrayer,
                                playAdhanAudio = state.isNextAdhanEnabled,
                                isMuted = state.isMuted,
                                onSkipAudio = { prayerName ->
                                    state.nextPrayer?.let { next ->
                                        onSkipNextAudio(prayerName, next.date)
                                        onShowToast(prayerName)
                                    }
                                },
                                onResetDate = onResetDate,
                                accentColor = Color.White,
                                showIdleState = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Glass Surface: Detailed calendar and calculation method info
            val panelShape = RoundedCornerShape(32.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp) // Extra padding as requested (+24dp)
                    .liquidGlass(panelShape, glassTheme),
                color = Color.Transparent,
                shape = panelShape
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Date picker/strip for selecting different days
                    ModernCalendarStrip(
                        selectedDate = state.selectedDate,
                        availableDays = availableDays,
                        isHijriSelected = state.isHijriSelected,
                        onToggleCalendarType = onToggleCalendarType,
                        onDateSelected = onDateSelected,
                        contentColor = contentColor
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Explicit list of prayer times for the selected day
                    state.currentPrayerDay?.let { prayerDay ->
                        PrayerTimeList(
                            day = prayerDay,
                            currentPrayerType = if (state.selectedDate == LocalDate.now()) state.currentPrayer?.type else null,
                            contentColor = contentColor
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Interaction card for viewing/editing calculation methods
                    CalculationMethodCard(
                        settings = settings,
                        calculationMethods = calculationMethods,
                        onClick = onMethodClick,
                        contentColor = contentColor,
                        glassTheme = glassTheme
                    )
                }
            }
            
            // Add navigation bar padding inside scrollable area
            Spacer(Modifier.navigationBarsPadding())
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
