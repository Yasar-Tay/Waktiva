package com.ybugmobile.waktiva.ui.adhan

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.notification.NotificationHelper
import com.ybugmobile.waktiva.data.worker.AdhanWorker
import com.ybugmobile.waktiva.domain.model.PrayerDay
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.domain.repository.PrayerRepository
import com.ybugmobile.waktiva.ui.theme.WaktivaTheme
import com.ybugmobile.waktiva.ui.theme.getGradientForTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class AdhanActivity : ComponentActivity() {

    @Inject
    lateinit var prayerRepository: PrayerRepository

    private var prayerNameState = mutableStateOf("Prayer")

    private val workObserver = Observer<List<WorkInfo>> { workInfoList ->
        val isActive = workInfoList.any {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }
        if (!isActive && workInfoList.isNotEmpty()) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        showOnLockScreen()
        super.onCreate(savedInstanceState)

        updatePrayerName(intent)

        setContent {
            val prayerDays by prayerRepository.getPrayerDays().collectAsState(initial = emptyList())
            val currentDay = prayerDays.find { it.date == LocalDate.now() }

            WaktivaTheme {
                AdhanScreen(
                    prayerName = prayerNameState.value,
                    currentPrayerDay = currentDay,
                    onDismiss = {
                        WorkManager.getInstance(this).cancelUniqueWork(AdhanWorker.WORK_NAME)
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updatePrayerName(intent)
    }

    private fun updatePrayerName(intent: android.content.Intent?) {
        intent?.getStringExtra(NotificationHelper.EXTRA_PRAYER_NAME)?.let {
            prayerNameState.value = it
        }
    }

    override fun onStart() {
        super.onStart()
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(AdhanWorker.WORK_NAME)
            .observe(this, workObserver)
    }

    override fun onStop() {
        super.onStop()
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(AdhanWorker.WORK_NAME)
            .removeObserver(workObserver)
    }

    private fun showOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }
}

@Composable
fun AdhanScreen(
    prayerName: String,
    currentPrayerDay: PrayerDay?,
    onDismiss: () -> Unit
) {
    val prayerType = PrayerType.fromString(prayerName)
    val context = androidx.compose.ui.platform.LocalContext.current
    val displayedPrayerName = prayerType?.getDisplayName(context) ?: prayerName

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    var currentTimeStr by remember { mutableStateOf("") }
    var currentDateStr by remember { mutableStateOf("") }
    var currentTime by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            currentDateStr = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
            currentTime = LocalTime.now()
            delay(1000)
        }
    }

    val backgroundGradient = getGradientForTime(currentTime, currentPrayerDay)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundGradient)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(400.dp)
                .scale(pulseScale)
                .background(Color.White.copy(alpha = 0.03f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 60.dp)
            ) {
                Text(
                    text = currentTimeStr,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 92.sp,
                        fontWeight = FontWeight.ExtraLight,
                        letterSpacing = (-4).sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentDateStr,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when (prayerType) {
                        PrayerType.FAJR -> ImageVector.vectorResource(R.drawable.haze_day_rotated)
                        PrayerType.SUNRISE -> ImageVector.vectorResource(R.drawable.sunrise)
                        PrayerType.DHUHR -> ImageVector.vectorResource(R.drawable.clear_day)
                        PrayerType.ASR -> ImageVector.vectorResource(R.drawable.clear_day)
                        PrayerType.MAGHRIB -> ImageVector.vectorResource(R.drawable.sunset)
                        PrayerType.ISHA -> ImageVector.vectorResource(R.drawable.clear_night)
                        else -> Icons.Rounded.NotificationsActive
                    },
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(80.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.adhan_its_time_for).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = displayedPrayerName.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = Color.White
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(pulseScale)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    )

                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier.size(84.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = stringResource(R.string.adhan_stop),
                                modifier = Modifier.size(32.dp),
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.adhan_stop).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AdhanScreenPreview() {
    WaktivaTheme {
        AdhanScreen(
            prayerName = "MAGHRIB",
            currentPrayerDay = PrayerDay(
                date = LocalDate.now(),
                hijriDate = null,
                timings = mapOf(
                    PrayerType.FAJR to LocalTime.of(5, 0),
                    PrayerType.SUNRISE to LocalTime.of(6, 30),
                    PrayerType.DHUHR to LocalTime.of(12, 30),
                    PrayerType.ASR to LocalTime.of(15, 45),
                    PrayerType.MAGHRIB to LocalTime.of(18, 15),
                    PrayerType.ISHA to LocalTime.of(19, 45)
                )
            ),
            onDismiss = {}
        )
    }
}
