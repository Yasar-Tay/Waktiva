package com.ybugmobile.vaktiva.ui.welcome

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.ui.settings.AudioSettingsViewModel
import com.ybugmobile.vaktiva.ui.settings.SettingsViewModel

private val WelcomeGradientStart = Color(0xFF0F172A)
private val WelcomeGradientEnd = Color(0xFF1E293B)
private val AccentColor = Color(0xFF38BDF8)

enum class WelcomeStep {
    INTRO,
    PERMISSIONS,
    PREFERENCES
}

@Composable
fun WelcomeScreen(
    onSetupComplete: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    audioViewModel: AudioSettingsViewModel = hiltViewModel()
) {
    var currentStep by remember { mutableStateOf(WelcomeStep.INTRO) }
    val backgroundBrush = Brush.verticalGradient(listOf(WelcomeGradientStart, WelcomeGradientEnd))

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                (slideOutHorizontally { width -> -width } + fadeOut())
            },
            label = "StepTransition"
        ) { step ->
            when (step) {
                WelcomeStep.INTRO -> IntroStep(onNext = { currentStep = WelcomeStep.PERMISSIONS })
                WelcomeStep.PERMISSIONS -> PermissionsStep(onNext = { currentStep = WelcomeStep.PREFERENCES })
                WelcomeStep.PREFERENCES -> PreferencesStep(
                    settingsViewModel = settingsViewModel,
                    audioViewModel = audioViewModel,
                    onComplete = {
                        settingsViewModel.setSetupComplete(true)
                        onSetupComplete()
                    }
                )
            }
        }
    }
}

@Composable
private fun IntroStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = AccentColor.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentColor, modifier = Modifier.size(64.dp))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.welcome_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color.Black),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.welcome_begin), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    
    // Separate states for each permission to allow independent triggering via cards
    val locationPermissionState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )
    
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null

    val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    var alarmGranted by remember { 
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() 
            else true
        ) 
    }

    // Observe lifecycle to refresh alarm permission status when returning from settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmGranted = alarmManager.canScheduleExactAlarms()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isLocationGranted = locationPermissionState.allPermissionsGranted
    val isNotificationGranted = notificationPermissionState?.status?.isGranted ?: true
    val allGranted = isLocationGranted && isNotificationGranted && alarmGranted

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.welcome_precision_settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.welcome_precision_desc), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PermissionCard(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.welcome_loc_title),
                description = stringResource(R.string.welcome_loc_desc),
                isGranted = isLocationGranted,
                onClick = {
                    if (!isLocationGranted) {
                        locationPermissionState.launchMultiplePermissionRequest()
                    }
                }
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.welcome_notif_title),
                    description = stringResource(R.string.welcome_notif_desc),
                    isGranted = isNotificationGranted,
                    onClick = {
                        if (!isNotificationGranted) {
                            notificationPermissionState?.launchPermissionRequest()
                        }
                    }
                )
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionCard(
                    icon = Icons.Default.Alarm,
                    title = stringResource(R.string.welcome_alarm_title),
                    description = stringResource(R.string.welcome_alarm_desc),
                    isGranted = alarmGranted,
                    onClick = {
                        if (!alarmGranted) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (allGranted) {
                    onNext()
                } else {
                    // Trigger first missing permission if the user clicks the main button
                    if (!isLocationGranted) {
                        locationPermissionState.launchMultiplePermissionRequest()
                    } else if (!isNotificationGranted) {
                        notificationPermissionState?.launchPermissionRequest()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmGranted) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (allGranted) AccentColor else Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (allGranted) stringResource(R.string.welcome_continue_btn) else stringResource(R.string.welcome_grant_btn),
                fontWeight = FontWeight.Bold,
                color = if (allGranted) Color.Black else Color.Black
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PreferencesStep(
    settingsViewModel: SettingsViewModel,
    audioViewModel: AudioSettingsViewModel,
    onComplete: () -> Unit
) {
    val settings by settingsViewModel.settings.collectAsState(null)
    val audioItems by audioViewModel.audioItems.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).systemBarsPadding()
    ) {
        Text(stringResource(R.string.welcome_personalize), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text(stringResource(R.string.welcome_audio_desc), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
        
        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.05f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.welcome_adhan_audio), fontWeight = FontWeight.Bold, color = Color.White)
                    Text(stringResource(R.string.welcome_adhan_audio_desc), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                }
                Switch(
                    checked = settings?.playAdhanAudio ?: true,
                    onCheckedChange = { settingsViewModel.setPlayAdhanAudio(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = AccentColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.welcome_select_sound), style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(audioItems) { item ->
                AudioSelectionItem(
                    name = item.name,
                    isSelected = item.isSelected,
                    isPlaying = item.isPlaying,
                    onSelect = { audioViewModel.selectAudio(item.path) },
                    onTogglePreview = { audioViewModel.togglePreview(item.path) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color.Black),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.welcome_finish), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: String, description: String, isGranted: Boolean, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .border(1.dp, if (isGranted) AccentColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = if (isGranted) AccentColor.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (isGranted) AccentColor else Color.White.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
            if (isGranted) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentColor)
        }
    }
}

@Composable
private fun AudioSelectionItem(name: String, isSelected: Boolean, isPlaying: Boolean, onSelect: () -> Unit, onTogglePreview: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .border(1.dp, if (isSelected) AccentColor.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AccentColor.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) AccentColor else Color.White.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(name, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = onTogglePreview) {
                Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, tint = if (isPlaying) Color.Red else AccentColor)
            }
        }
    }
}
