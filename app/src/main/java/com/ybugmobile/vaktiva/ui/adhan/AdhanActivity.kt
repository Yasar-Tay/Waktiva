package com.ybugmobile.vaktiva.ui.adhan

import android.app.KeyguardManager
import android.content.ComponentName
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.notification.NotificationHelper
import com.ybugmobile.vaktiva.domain.model.PrayerDay
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.domain.repository.PrayerRepository
import com.ybugmobile.vaktiva.service.AdhanService
import com.ybugmobile.vaktiva.ui.theme.VaktivaTheme
import com.ybugmobile.vaktiva.ui.theme.getGradientForTime
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

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        showOnLockScreen()
        super.onCreate(savedInstanceState)
        
        val prayerName = intent.getStringExtra(NotificationHelper.EXTRA_PRAYER_NAME) ?: "Prayer"

        setContent {
            val prayerDays by prayerRepository.getPrayerDays().collectAsState(initial = emptyList())
            val currentDay = prayerDays.find { it.date == LocalDate.now() }
            
            var adhanTitle by remember { mutableStateOf<String?>(null) }
            var adhanArtist by remember { mutableStateOf<String?>(null) }

            VaktivaTheme {
                AdhanScreen(
                    prayerName = prayerName,
                    adhanTitle = adhanTitle,
                    adhanArtist = adhanArtist,
                    currentPrayerDay = currentDay,
                    onDismiss = { 
                        controller?.stop()
                        finish() 
                    }
                )
            }
            
            // Listen for metadata changes from the controller
            LaunchedEffect(controller) {
                controller?.let { c ->
                    fun updateMetadata() {
                        adhanTitle = c.currentMediaItem?.mediaMetadata?.title?.toString()
                        adhanArtist = c.currentMediaItem?.mediaMetadata?.artist?.toString()
                    }
                    updateMetadata()
                    c.addListener(object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                            updateMetadata()
                        }
                        override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                            updateMetadata()
                        }
                    })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, AdhanService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (!isPlaying) finish()
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                            finish()
                        }
                    }
                })
                
                val state = controller?.playbackState
                if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
                    finish()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
    }

    private fun showOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }
}

@Composable
fun AdhanScreen(
    prayerName: String, 
    adhanTitle: String?,
    adhanArtist: String?,
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
            currentDateStr = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now).uppercase()
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
        // Decorative Background Elements
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
            // 1. Time & Date Section
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

            // 2. Prayer Name Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Relevant Icon
                Icon(
                    imageVector = when(prayerType) {
                        PrayerType.FAJR -> Icons.Rounded.NightsStay
                        PrayerType.SUNRISE -> Icons.Rounded.WbTwilight
                        PrayerType.DHUHR -> Icons.Rounded.WbSunny
                        PrayerType.ASR -> Icons.Rounded.WbSunny
                        PrayerType.MAGHRIB -> Icons.Rounded.WbTwilight
                        PrayerType.ISHA -> Icons.Rounded.NightsStay
                        else -> Icons.Rounded.NotificationsActive
                    },
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.adhan_its_time_for).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = displayedPrayerName.uppercase(),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = Color.White
                )
                
                // Track Metadata (Title & Artist)
                if (adhanTitle != null || adhanArtist != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (adhanTitle != null) {
                            Text(
                                text = adhanTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (adhanArtist != null) {
                            Text(
                                text = adhanArtist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 3. Action Section: STOP Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                // Outer Pulse Ring
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
                    text = stringResource(R.string.adhan_stop).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
