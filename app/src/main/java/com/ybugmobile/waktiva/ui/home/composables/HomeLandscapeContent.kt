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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ybugmobile.waktiva.data.local.preferences.UserSettings
import com.ybugmobile.waktiva.ui.home.HomeViewState
import com.ybugmobile.waktiva.ui.theme.GlassTheme
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
    onShowToast: (String) -> Unit
) {
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
                // LEFT HALF: Circular Visualization
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(320.dp), // Increased size for tablet visibility
                        contentAlignment = Alignment.Center
                    ) {
                        state.currentPrayerDay?.let { prayerDay ->
                            PrayerCircleVisualization(
                                day = prayerDay,
                                currentTime = if (state.selectedDate == LocalDate.now()) localTime else LocalTime.MIDNIGHT,
                                nextPrayer = if (state.selectedDate == LocalDate.now()) state.nextPrayer else null,
                                currentPrayer = if (state.selectedDate == LocalDate.now()) state.currentPrayer else null,
                                isSelectedDayToday = state.selectedDate == LocalDate.now(),
                                isHijriVisible = state.isHijriSelected,
                                onToggleHijri = { onToggleCalendarType(!state.isHijriSelected) },
                                contentColor = contentColor,
                                isMuted = state.isMuted,
                                playAdhanAudio = settings?.playAdhanAudio ?: false,
                                onSkipAudio = { prayerName ->
                                    state.nextPrayer?.let { next ->
                                        onSkipNextAudio(prayerName, next.date)
                                    }
                                }
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
                                        scaleX = 0.6f
                                        scaleY = 0.6f
                                    }
                            )
                        }

                        // Right Part: Weather
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            if (state.isNetworkAvailable) {
                                WeatherSection(
                                    temperature = state.temperature,
                                    condition = state.weatherCondition,
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

                    Spacer(modifier = Modifier.height(32.dp))

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
                                playAdhanAudio = settings?.playAdhanAudio ?: false,
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp), // Extra padding as requested (+24dp)
                color = glassTheme.containerColor,
                shape = RoundedCornerShape(32.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    glassTheme.borderColor
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Date picker/strip for selecting different days
                    ModernCalendarStrip(
                        selectedDate = state.selectedDate,
                        availableDays = allDays.filter { !it.date.isBefore(LocalDate.now()) },
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
                            contentColor = contentColor,
                            highlightColor = contentColor.copy(alpha = 0.15f)
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
