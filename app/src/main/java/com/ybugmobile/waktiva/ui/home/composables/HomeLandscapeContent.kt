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
    onShowSnackbar: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .displayCutoutPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(start = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(280.dp),
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

                Spacer(modifier = Modifier.width(32.dp))

                Box(modifier = Modifier.weight(1f)) {
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
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                        availableDays = allDays.filter { !it.date.isBefore(LocalDate.now()) },
                        isHijriSelected = state.isHijriSelected,
                        onToggleCalendarType = onToggleCalendarType,
                        onDateSelected = onDateSelected,
                        contentColor = contentColor
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    state.currentPrayerDay?.let { prayerDay ->
                        PrayerTimeList(
                            day = prayerDay,
                            currentPrayerType = if (state.selectedDate == LocalDate.now()) state.currentPrayer?.type else null,
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

        // Moon Phase in Top Middle for Landscape
        MoonPhaseView(
            moonPhase = state.moonPhase,
            contentColor = contentColor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .graphicsLayer {
                    translationY = -scrollState.value.toFloat()
                }
        )

        // Weather Section in Top Right for Landscape
        if (state.isNetworkAvailable) {
            WeatherSection(
                temperature = state.temperature,
                condition = state.weatherCondition,
                contentColor = contentColor,
                currentTime = localTime,
                currentPrayerDay = state.currentPrayerDay,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(top = 6.dp, end = 24.dp)
                    .graphicsLayer {
                        translationY = -scrollState.value.toFloat()
                    }
            )
        }
    }
}
