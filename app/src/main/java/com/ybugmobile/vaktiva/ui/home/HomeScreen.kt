package com.ybugmobile.vaktiva.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.local.entity.PrayerDayEntity
import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!permissionState.allPermissionsGranted) {
                    Spacer(modifier = Modifier.height(24.dp))
                    PermissionRequestCard(permissionState)
                }

                Spacer(modifier = Modifier.height(48.dp))
                
                // Location & Date
                val locationText = settings?.locationName ?: stringResource(R.string.home_unknown_location)
                val coordinatesText = if (settings != null) {
                    String.format(Locale.US, "%.4f, %.4f", settings.latitude, settings.longitude)
                } else ""

                Text(
                    text = locationText,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (coordinatesText.isNotEmpty()) {
                    Text(
                        text = coordinatesText,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
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
                    style = MaterialTheme.typography.bodyLarge
                )
                
                if (currentDay != null) {
                    Text(
                        text = formatHijriDate(currentDay.hijriDate),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Circular Prayer Visualization with Countdown
                if (currentDay != null) {
                    PrayerCircleVisualization(
                        day = currentDay,
                        currentTime = if (selectedDate == LocalDate.now()) currentTime.toLocalTime() else LocalTime.MIDNIGHT,
                        nextPrayer = if (selectedDate == LocalDate.now()) nextPrayer else null,
                        isSelectedDayToday = selectedDate == LocalDate.now()
                    )
                } else {
                    Spacer(modifier = Modifier.height(250.dp))
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
        "Safar" to "Safer",
        "Rabi' al-awwal" to "Rebiülevvel",
        "Rabi' ath-thani" to "Rebiülahir",
        "Jumada al-ula" to "Cemaziyelevvel",
        "Jumada al-akhira" to "Cemaziyelahir",
        "Rajab" to "Recep",
        "Sha'ban" to "Şaban",
        "Ramadan" to "Ramazan",
        "Shawwal" to "Şevval",
        "Dhu al-Qi'dah" to "Zilkade",
        "Dhu al-Hijjah" to "Zilhicce"
    )

    months.forEach { (en, tr) ->
        formatted = formatted.replace(en, tr)
    }
    return formatted
}

@Composable
fun ModernCalendarStrip(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val calendarDays = remember {
        (0..13).map { today.plusDays(it.toLong()) }
    }
    
    val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    val dayNameFormatter = DateTimeFormatter.ofPattern("EEE")

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = selectedDate.format(monthYearFormatter),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(calendarDays) { date ->
                val isSelected = date == selectedDate
                val isToday = date == today
                
                Surface(
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(54.dp)
                        .clickable { onDateSelected(date) }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = date.format(dayNameFormatter).uppercase(),
                            color = if (isSelected) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = date.dayOfMonth.toString(),
                            color = if (isSelected) Color.Black else Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (isToday) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(if (isSelected) Color.Blue else Color.Cyan, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrayerCircleVisualization(
    day: PrayerDayEntity,
    currentTime: LocalTime,
    nextPrayer: NextPrayerInfo?,
    isSelectedDayToday: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    fun parseTime(timeStr: String): LocalTime {
        val cleaned = timeStr.split(" ")[0]
        return LocalTime.parse(cleaned, formatter)
    }

    val sunrise = parseTime(day.sunrise)
    val sunset = parseTime(day.maghrib)

    val prayers = listOf(
        stringResource(R.string.prayer_fajr) to parseTime(day.fajr),
        stringResource(R.string.prayer_sunrise) to sunrise,
        stringResource(R.string.prayer_dhuhr) to parseTime(day.dhuhr),
        stringResource(R.string.prayer_asr) to parseTime(day.asr),
        stringResource(R.string.prayer_maghrib) to sunset,
        stringResource(R.string.prayer_isha) to parseTime(day.isha)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 45.dp.toPx()
            val strokeWidth = 5.dp.toPx()
            
            // Draw background arcs for Day and Night
            val sunriseMinutes = sunrise.hour * 60 + sunrise.minute
            val sunsetMinutes = sunset.hour * 60 + sunset.minute
            
            val startAngleDay = (sunriseMinutes.toFloat() / (24 * 60)) * 360f - 90f
            val endAngleDay = (sunsetMinutes.toFloat() / (24 * 60)) * 360f - 90f
            var sweepAngleDay = endAngleDay - startAngleDay
            if (sweepAngleDay < 0) sweepAngleDay += 360f
            
            // Night Arc (Sunset to Sunrise)
            drawArc(
                color = Color(0xFF1A237E).copy(alpha = 0.3f),
                startAngle = endAngleDay,
                sweepAngle = 360f - sweepAngleDay,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth)
            )

            // Day Arc (Sunrise to Sunset)
            drawArc(
                color = Color(0xFFFFF176).copy(alpha = 0.3f),
                startAngle = startAngleDay,
                sweepAngle = sweepAngleDay,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth)
            )

            // Draw the 24h circle outline
            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Function to calculate position on circle
            fun getOffset(time: LocalTime, r: Float): Offset {
                val totalMinutes = time.hour * 60 + time.minute
                val angle = (totalMinutes.toFloat() / (24 * 60)) * 360f - 90f
                val angleRad = Math.toRadians(angle.toDouble())
                return Offset(
                    x = center.x + r * cos(angleRad).toFloat(),
                    y = center.y + r * sin(angleRad).toFloat()
                )
            }

            // Draw current time progress marker (Cyan) only if today is selected
            if (isSelectedDayToday) {
                val currentPos = getOffset(currentTime, radius)
                drawCircle(
                    color = Color.Cyan,
                    radius = 6.dp.toPx(),
                    center = currentPos
                )
            }

            // Draw prayer dots and labels
            prayers.forEach { (name, time) ->
                val pos = getOffset(time, radius)
                val isNext = nextPrayer?.time == time.format(formatter) && isSelectedDayToday

                // Dot
                drawCircle(
                    color = if (isNext) Color.Yellow else Color.White,
                    radius = if (isNext) 8.dp.toPx() else 5.dp.toPx(),
                    center = pos
                )

                // Label (Tag)
                val labelPos = getOffset(time, radius + 28.dp.toPx())
                val textLayoutResult = textMeasurer.measure(
                    text = name,
                    style = TextStyle(
                        color = if (isNext) Color.Yellow else Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                    )
                )
                
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        labelPos.x - textLayoutResult.size.width / 2,
                        labelPos.y - textLayoutResult.size.height / 2
                    )
                )
            }
        }

        // Center Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isSelectedDayToday && nextPrayer != null) {
                val prayerNameRes = when (nextPrayer.name) {
                    "Fajr" -> R.string.prayer_fajr
                    "Sunrise" -> R.string.prayer_sunrise
                    "Dhuhr" -> R.string.prayer_dhuhr
                    "Asr" -> R.string.prayer_asr
                    "Maghrib" -> R.string.prayer_maghrib
                    "Isha" -> R.string.prayer_isha
                    else -> -1
                }
                val prayerName = if (prayerNameRes != -1) stringResource(prayerNameRes) else nextPrayer.name
                
                Text(
                    text = prayerName,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = formatRemainingTime(nextPrayer.remainingMillis),
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
            } else if (!isSelectedDayToday) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "VAKTIVA",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            }
        }
    }
}

@Composable
fun CalculationMethodSelector(
    currentMethodId: Int,
    methods: List<Pair<String, Int>>,
    onMethodSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val methodNames = mapOf(
        3 to stringResource(R.string.method_mwl),
        2 to stringResource(R.string.method_isna),
        5 to stringResource(R.string.method_egypt),
        4 to stringResource(R.string.method_makkah),
        1 to stringResource(R.string.method_karachi),
        7 to stringResource(R.string.method_tehran),
        8 to stringResource(R.string.method_gulf),
        9 to stringResource(R.string.method_kuwait),
        10 to stringResource(R.string.method_qatar),
        11 to stringResource(R.string.method_singapore),
        13 to stringResource(R.string.method_turkey)
    )

    val currentMethodName = methodNames[currentMethodId] ?: methods.find { it.second == currentMethodId }?.first ?: "Unknown Method"

    Box {
        Surface(
            onClick = { expanded = true },
            color = Color.White.copy(alpha = 0.15f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentMethodName,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            methods.forEach { (_, id) ->
                val name = methodNames[id] ?: "Unknown"
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onMethodSelected(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestCard(permissionState: MultiplePermissionsState) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_permissions_required),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(R.string.home_permissions_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(
                onClick = {
                    if (permissionState.shouldShowRationale) {
                        permissionState.launchMultiplePermissionRequest()
                    } else {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.home_grant_permissions))
            }
        }
    }
}

@Composable
fun PrayerTimeList(
    day: PrayerDayEntity,
    nextPrayerName: String?,
    currentMethodId: Int,
    methods: List<Pair<String, Int>>,
    onMethodSelected: (Int) -> Unit
) {
    data class PrayerItem(val key: String, val resId: Int, val time: String, val icon: ImageVector)
    
    val prayers = listOf(
        PrayerItem("Fajr", R.string.prayer_fajr, day.fajr, Icons.Default.WbTwilight),
        PrayerItem("Sunrise", R.string.prayer_sunrise, day.sunrise, Icons.Default.WbSunny),
        PrayerItem("Dhuhr", R.string.prayer_dhuhr, day.dhuhr, Icons.Default.LightMode),
        PrayerItem("Asr", R.string.prayer_asr, day.asr, Icons.Default.WbSunny),
        PrayerItem("Maghrib", R.string.prayer_maghrib, day.maghrib, Icons.Default.WbTwilight),
        PrayerItem("Isha", R.string.prayer_isha, day.isha, Icons.Default.NightsStay)
    )

    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_prayer_provider),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 12.sp,

                )
                CalculationMethodSelector(
                    currentMethodId = currentMethodId,
                    methods = methods,
                    onMethodSelected = onMethodSelected,
                )
            }
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            prayers.forEach { item ->
                val isNext = item.key == nextPrayerName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (isNext) Color.Yellow else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(item.resId),
                            color = if (isNext) Color.Yellow else Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Text(
                        text = item.time,
                        color = if (isNext) Color.Yellow else Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun getGradientForTime(currentTime: LocalTime, day: PrayerDayEntity?): Brush {
    if (day == null) return Brush.verticalGradient(listOf(Color(0xFF1e3c72), Color(0xFF2a5298)))
    
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    fun parse(s: String) = LocalTime.parse(s.split(" ")[0], formatter)
    
    val fajr = parse(day.fajr)
    val sunrise = parse(day.sunrise)
    val dhuhr = parse(day.dhuhr)
    val asr = parse(day.asr)
    val maghrib = parse(day.maghrib)
    val isha = parse(day.isha)

    return when {
        currentTime.isBefore(fajr) -> Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43))) // Deep Night
        currentTime.isBefore(sunrise) -> Brush.verticalGradient(listOf(Color(0xFF2C3E50), Color(0xFFFD746C))) // Dawn
        currentTime.isBefore(dhuhr) -> Brush.verticalGradient(listOf(Color(0xFFF3904F), Color(0xFF3B4371))) // Morning
        currentTime.isBefore(asr) -> Brush.verticalGradient(listOf(Color(0xFF4CA1AF), Color(0xFFC4E0E5))) // Midday
        currentTime.isBefore(maghrib) -> Brush.verticalGradient(listOf(Color(0xFFF16529), Color(0xFFE44D26))) // Golden Hour
        currentTime.isBefore(isha) -> Brush.verticalGradient(listOf(Color(0xFFE94057), Color(0xFFF27121))) // Sunset
        else -> Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF2C5364))) // Night After Isha
    }
}

fun formatRemainingTime(millis: Long): String {
    val seconds = floor(millis / 1000.0).toLong()
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
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