package com.ybugmobile.vaktiva.ui.home

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import com.ybugmobile.vaktiva.ui.home.composables.ModernCalendarStrip
import com.ybugmobile.vaktiva.ui.home.composables.PrayerCircleVisualization
import com.ybugmobile.vaktiva.ui.home.composables.PermissionRequestCard
import com.ybugmobile.vaktiva.ui.home.composables.PrayerTimeList
import com.ybugmobile.vaktiva.ui.theme.getGradientForTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val currentDay by viewModel.currentPrayerDay.collectAsState(initial = null)
    val nextPrayer by viewModel.nextPrayerInfo.collectAsState(initial = null)
    val currentTime by viewModel.currentTime.collectAsState()
    val settings by viewModel.settings.collectAsState(initial = null)
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val allDays by viewModel.allPrayerDays.collectAsState(initial = emptyList())

    HomeScreenContent(
        currentDay = currentDay,
        nextPrayer = nextPrayer,
        currentTime = currentTime,
        settings = settings,
        isRefreshing = isRefreshing,
        selectedDate = selectedDate,
        allDays = allDays,
        calculationMethods = viewModel.calculationMethods,
        onRefresh = { viewModel.refresh() },
        onMethodSelected = { viewModel.updateCalculationMethod(it) },
        onDateSelected = { viewModel.selectDate(it) }
    )
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    currentDay: PrayerDayEntity?,
    nextPrayer: NextPrayerInfo?,
    currentTime: LocalDateTime,
    settings: UserSettings?,
    isRefreshing: Boolean,
    selectedDate: LocalDate,
    allDays: List<PrayerDayEntity>,
    calculationMethods: List<Pair<String, Int>>,
    onRefresh: () -> Unit,
    onMethodSelected: (Int) -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    val backgroundGradient = getGradientForTime(currentTime.toLocalTime(), currentDay)
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundGradient)
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (!permissionState.allPermissionsGranted) {
                    Spacer(modifier = Modifier.height(24.dp))
                    PermissionRequestCard(permissionState)
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Location & Date
                val locationText =
                    settings?.locationName ?: stringResource(R.string.home_unknown_location)
                val coordinatesText = if (settings != null) {
                    String.format(Locale.US, "%.4f, %.4f", settings.latitude, settings.longitude)
                } else ""

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = locationText,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Start
                    )
                }

                // Display selected date's info
                val displayDate = if (selectedDate == LocalDate.now()) {
                    currentTime.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
                } else {
                    selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
                }

                Text(
                    text = displayDate,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 32.dp)
                )

                if (currentDay != null) {
                    Text(
                        text = formatHijriDate(currentDay.hijriDate),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Circular Prayer Visualization with Countdown
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (currentDay != null) {
                        PrayerCircleVisualization(
                            day = currentDay,
                            currentTime = if (selectedDate == LocalDate.now()) currentTime.toLocalTime() else LocalTime.MIDNIGHT,
                            nextPrayer = if (selectedDate == LocalDate.now()) nextPrayer else null,
                            isSelectedDayToday = selectedDate == LocalDate.now(),
                            centerContent = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if ((selectedDate == LocalDate.now()) && nextPrayer != null) {
                                        val prayerName = stringResource(
                                            when (nextPrayer.name) {
                                                "Fajr" -> R.string.prayer_fajr
                                                "Sunrise" -> R.string.prayer_sunrise
                                                "Dhuhr" -> R.string.prayer_dhuhr
                                                "Asr" -> R.string.prayer_asr
                                                "Maghrib" -> R.string.prayer_maghrib
                                                "Isha" -> R.string.prayer_isha
                                                else -> R.string.app_name
                                            }
                                        )

                                        Text(
                                            prayerName,
                                            color = Color.White.copy(alpha = 0.8f),
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            formatRemainingTime(nextPrayer.remainingMillis),
                                            color = Color.White,
                                            style = MaterialTheme.typography.displayMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 36.sp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Schedule,
                                            null,
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Text(
                                            "VAKTIVA",
                                            color = Color.White.copy(alpha = 0.5f),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 4.sp
                                        )
                                    }
                                }
                            }
                        )
                    } else {
                        Spacer(modifier = Modifier.height(250.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Redesigned 14 Days Calendar
                ModernCalendarStrip(
                    selectedDate = selectedDate,
                    onDateSelected = onDateSelected
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Prayer Times List with integrated Method Selector
                currentDay?.let { day ->
                    PrayerTimeList(
                        day = day,
                        nextPrayerName = if (selectedDate == LocalDate.now()) nextPrayer?.name else null,
                        currentMethodId = settings?.calculationMethod ?: 2,
                        methods = calculationMethods,
                        onMethodSelected = onMethodSelected
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun formatHijriDate(date: String): String {
    val locale = Locale.getDefault()
    if (locale.language != "tr") return date

    var formatted = date
    val months = mapOf(
        "Muharram" to "Muharrem",
        "Muḥarram" to "Muharrem",
        "Safar" to "Safer",
        "Ṣafar" to "Safer",
        "Rabi' al-awwal" to "Rebiülevvel",
        "Rabīʿ al-Awwal" to "Rebiülevvel",
        "Rabi' ath-thani" to "Rebiülahir",
        "Rabīʿ al-Thānī" to "Rebiülahir",
        "Jumādā al-Ūlā" to "Cemaziyelevvel",
        "Jumada al-ula" to "Cemaziyelevvel",
        "Jumada al-akhira" to "Cemaziyelahir",
        "Jumādā al-Ākhirah" to "Cemaziyelahir",
        "Rajab" to "Recep",
        "Sha'ban" to "Şaban",
        "Shaʿbān" to "Şaban",
        "Ramadan" to "Ramazan",
        "Ramaḍān" to "Ramazan",
        "Shawwal" to "Şevval",
        "Shawwāl" to "Şevval",
        "Dhu al-Qi'dah" to "Zilkade",
        "Dhū al-Qaʿdah" to "Zilkade",
        "Dhū al-Qaʿdah" to "Zilkade",
        "Dhū al-Ḥijjah" to "Zilhicce"
    )

    months.forEach { (en, tr) ->
        formatted = formatted.replace(en, tr)
    }
    return formatted
}

fun formatRemainingTime(millis: Long): String {
    val totalSeconds = millis / 1000
    if (totalSeconds < 0) return "00:00:00"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    val dummyDay = PrayerDayEntity(
        date = LocalDate.now().toString(),
        fajr = "05:30",
        sunrise = "07:00",
        dhuhr = "12:30",
        asr = "15:45",
        maghrib = "18:15",
        isha = "19:45",
        hijriDate = "18 Sha'ban 1446"
    )
    val dummyNextPrayer = NextPrayerInfo(
        name = "Dhuhr",
        time = "12:30",
        remainingMillis = 3600000
    )
    val dummySettings = UserSettings(
        madhab = 1,
        calculationMethod = 13,
        latitude = 41.0082,
        longitude = 28.9784,
        locationName = "Istanbul, Turkey",
        language = "tr"
    )

    MaterialTheme {
        HomeScreenContent(
            currentDay = dummyDay,
            nextPrayer = dummyNextPrayer,
            currentTime = LocalDateTime.now(),
            settings = dummySettings,
            isRefreshing = false,
            selectedDate = LocalDate.now(),
            allDays = listOf(dummyDay),
            calculationMethods = listOf("Turkey" to 13),
            onRefresh = {},
            onMethodSelected = {},
            onDateSelected = {}
        )
    }
}
