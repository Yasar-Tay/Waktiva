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
import androidx.compose.runtime.*
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

@Composable
fun HomePortraitContent(
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
    onShowSnackbar: (String) -> Unit
) {
    // Optimization: Pre-calculate values that depend on state to avoid redundant work in sub-composables
    val isToday = remember(state.selectedDate) { state.selectedDate == LocalDate.now() }
    val availableDays = remember(allDays) { allDays.filter { !it.date.isBefore(LocalDate.now()) } }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
                .systemBarsPadding()
                .verticalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopStart
            ) {
                HomeHeader(
                    locationName = state.locationName,
                    date = state.selectedDate,
                    hijriDate = state.effectiveHijriDate,
                    contentColor = contentColor,
                    onStatusClick = onStatusClick,
                    isNetworkAvailable = state.isNetworkAvailable,
                    isLocationEnabled = state.isLocationEnabled,
                    isLocationPermissionGranted = state.isLocationPermissionGranted
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.isNetworkAvailable) {
                    WeatherSection(
                        temperature = state.temperature,
                        condition = state.weatherCondition,
                        contentColor = contentColor,
                        currentTime = localTime,
                        currentPrayerDay = state.currentPrayerDay,
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 10.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Spacer(modifier = Modifier.height(104.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    state.currentPrayerDay?.let { prayerDay ->
                        PrayerCircleVisualization(
                            day = prayerDay,
                            currentTime = if (isToday) localTime else LocalTime.MIDNIGHT,
                            nextPrayer = if (isToday) state.nextPrayer else null,
                            currentPrayer = if (isToday) state.currentPrayer else null,
                            isSelectedDayToday = isToday,
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

                Spacer(modifier = Modifier.height(16.dp))

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
                                    onShowSnackbar(prayerName)
                                }
                            },
                            onResetDate = onResetDate,
                            accentColor = Color.White,
                            showIdleState = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
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
                    ModernCalendarStrip(
                        selectedDate = state.selectedDate,
                        availableDays = availableDays,
                        isHijriSelected = state.isHijriSelected,
                        onToggleCalendarType = onToggleCalendarType,
                        onDateSelected = onDateSelected,
                        contentColor = contentColor
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    state.currentPrayerDay?.let { prayerDay ->
                        PrayerTimeList(
                            day = prayerDay,
                            currentPrayerType = if (isToday) state.currentPrayer?.type else null,
                            contentColor = contentColor,
                            highlightColor = contentColor.copy(alpha = 0.15f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    CalculationMethodCard(
                        settings = settings,
                        calculationMethods = calculationMethods,
                        onClick = onMethodClick,
                        contentColor = contentColor,
                        glassTheme = glassTheme
                    )
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }

        // Optimization: Defer translation reading using graphicsLayer lambda
        MoonPhaseView(
            moonPhase = state.moonPhase,
            contentColor = contentColor,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 24.dp)
                .graphicsLayer {
                    translationY = -scrollState.value.toFloat()
                    scaleX = 0.85f
                    scaleY = 0.85f
                }
        )
    }
}
