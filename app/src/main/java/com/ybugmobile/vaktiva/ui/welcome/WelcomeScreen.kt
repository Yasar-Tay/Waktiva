package com.ybugmobile.vaktiva.ui.welcome

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.ui.settings.AudioSettingsViewModel
import com.ybugmobile.vaktiva.ui.settings.SettingsViewModel
import com.ybugmobile.vaktiva.utils.PermissionUtils
import java.util.Locale as JavaLocale

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
    
    var locationDenialCount by remember { mutableStateOf(0) }
    var notificationDenialCount by remember { mutableStateOf(0) }

    val locationPermissionState = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )
    
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null

    var alarmGranted by remember { mutableStateOf(PermissionUtils.canScheduleExactAlarms(context)) }
    var batteryOptimizationIgnored by remember { mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context)) }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                alarmGranted = PermissionUtils.canScheduleExactAlarms(context)
                batteryOptimizationIgnored = PermissionUtils.isIgnoringBatteryOptimizations(context)
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
                        if (locationDenialCount >= 2 || locationPermissionState.shouldShowRationale) {
                            openAppSettings()
                        } else {
                            locationPermissionState.launchMultiplePermissionRequest()
                            locationDenialCount++
                        }
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
                            if (notificationDenialCount >= 2 || (notificationPermissionState?.status as? PermissionStatus.Denied)?.shouldShowRationale == true) {
                                openAppSettings()
                            } else {
                                notificationPermissionState?.launchPermissionRequest()
                                notificationDenialCount++
                            }
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
                            PermissionUtils.getExactAlarmSettingIntent(context)?.let {
                                context.startActivity(it)
                            }
                        }
                    }
                )
            }

            PermissionCard(
                icon = Icons.Default.BatteryChargingFull,
                title = stringResource(R.string.settings_battery_opt),
                description = if (batteryOptimizationIgnored) "Optimized for reliability" else "Disable optimization for reliable Adhans",
                isGranted = batteryOptimizationIgnored,
                onClick = {
                    if (!batteryOptimizationIgnored) {
                        context.startActivity(PermissionUtils.getIgnoreBatteryOptimizationIntent(context))
                    }
                }
            )
        }

        if (!allGranted) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.welcome_permission_later_info),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                onNext()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (allGranted) AccentColor else Color.White.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (allGranted) stringResource(R.string.welcome_continue_btn) else stringResource(R.string.welcome_skip_btn),
                fontWeight = FontWeight.Bold,
                color = if (allGranted) Color.Black else Color.White
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferencesStep(
    settingsViewModel: SettingsViewModel,
    audioViewModel: AudioSettingsViewModel,
    onComplete: () -> Unit
) {
    val settings by settingsViewModel.settings.collectAsState(null)
    val audioItems by audioViewModel.audioItems.collectAsState()
    val context = LocalContext.current
    
    val currentAppLocales = AppCompatDelegate.getApplicationLocales()
    val currentLanguage = if (!currentAppLocales.isEmpty) currentAppLocales.get(0)?.language ?: "system" else "system"

    var showMethodDialog by remember { mutableStateOf(false) }
    var showMadhabDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val methods = listOf(
        stringResource(R.string.method_mwl) to 3,
        stringResource(R.string.method_isna) to 2,
        stringResource(R.string.method_egypt) to 5,
        stringResource(R.string.method_makkah) to 4,
        stringResource(R.string.method_karachi) to 1,
        stringResource(R.string.method_tehran) to 7,
        stringResource(R.string.method_gulf) to 8,
        stringResource(R.string.method_kuwait) to 9,
        stringResource(R.string.method_qatar) to 10,
        stringResource(R.string.method_singapore) to 11,
        stringResource(R.string.method_turkey) to 13
    )

    val madhabOptions = listOf(
        stringResource(R.string.madhab_shafi) to 0,
        stringResource(R.string.madhab_hanafi) to 1
    )

    @Composable
    fun getNativeLanguageName(languageCode: String): String {
        return if (languageCode == "system") {
            val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale
            }
            val displayName = systemLocale?.getDisplayName(systemLocale) ?: ""
            "${stringResource(R.string.lang_system)} ($displayName)"
        } else {
            val locale = JavaLocale(languageCode)
            locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
        }
    }

    val languageOptions = listOf(
        "system" to getNativeLanguageName("system"),
        "en" to getNativeLanguageName("en"),
        "tr" to getNativeLanguageName("tr")
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).systemBarsPadding()
    ) {
        Text(stringResource(R.string.welcome_personalize), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Configure your prayer and calculation settings", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
        
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Preferences
            PreferenceSection(title = "General Settings") {
                WelcomeSettingsClickItem(
                    title = stringResource(R.string.settings_language),
                    subtitle = languageOptions.find { it.first == currentLanguage }?.second ?: getNativeLanguageName("system"),
                    icon = Icons.Default.Language,
                    onClick = { showLanguageDialog = true }
                )
                
                WelcomeSettingsClickItem(
                    title = stringResource(R.string.settings_madhab),
                    subtitle = madhabOptions.find { it.second == (settings?.madhab ?: 0) }?.first ?: "",
                    icon = Icons.Default.School,
                    onClick = { showMadhabDialog = true }
                )
                
                WelcomeSettingsClickItem(
                    title = stringResource(R.string.settings_method),
                    subtitle = methods.find { it.second == (settings?.calculationMethod ?: 3) }?.first ?: "",
                    icon = Icons.Default.Functions,
                    onClick = { showMethodDialog = true }
                )
            }

            // Adhan Audio
            PreferenceSection(title = "Adhan & Audio") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.05f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(id = R.string.settings_pre_adhan_warning), fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    stringResource(id = R.string.settings_pre_adhan_warning_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = settings?.enablePreAdhanWarning ?: true,
                                onCheckedChange = { audioViewModel.togglePreAdhanWarning(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = AccentColor)
                            )
                        }
                        
                        if (settings?.enablePreAdhanWarning == true) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(stringResource(id = R.string.audio_minutes_before), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Slider(
                                    value = (settings?.preAdhanWarningMinutes ?: 5).toFloat(),
                                    onValueChange = { audioViewModel.updatePreAdhanWarningMinutes(it.toInt()) },
                                    valueRange = 1f..30f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = AccentColor, activeTrackColor = AccentColor)
                                )
                                Text(
                                    text = "${settings?.preAdhanWarningMinutes ?: 5}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentColor
                                )
                            }
                        }
                    }
                }

                Text(stringResource(R.string.welcome_select_sound), style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.padding(top = 8.dp))
                
                audioItems.forEach { item ->
                    AudioSelectionItem(
                        name = item.name,
                        isSelected = item.isSelected,
                        isPlaying = item.isPlaying,
                        onSelect = { audioViewModel.selectAudio(item.path) },
                        onTogglePreview = { audioViewModel.togglePreview(item.path) }
                    )
                }
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

    // Dialogs
    if (showLanguageDialog) {
        WelcomeSelectionDialog(
            title = stringResource(R.string.settings_language),
            options = languageOptions,
            selectedKey = currentLanguage,
            onSelected = { lang ->
                val appLocale: LocaleListCompat = if (lang == "system") {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(lang)
                }
                AppCompatDelegate.setApplicationLocales(appLocale)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showMadhabDialog) {
        WelcomeSelectionDialog(
            title = stringResource(R.string.settings_madhab),
            options = madhabOptions,
            selectedKey = settings?.madhab ?: 0,
            onSelected = { settingsViewModel.setMadhab(it); showMadhabDialog = false },
            onDismiss = { showMadhabDialog = false }
        )
    }

    if (showMethodDialog) {
        WelcomeSelectionDialog(
            title = stringResource(R.string.settings_method),
            options = methods,
            selectedKey = settings?.calculationMethod ?: 3,
            onSelected = { settingsViewModel.setCalculationMethod(it); showMethodDialog = false },
            onDismiss = { showMethodDialog = false }
        )
    }
}

@Composable
private fun PreferenceSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = AccentColor,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
private fun WelcomeSettingsClickItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = AccentColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> WelcomeSelectionDialog(
    title: String,
    options: List<Pair<String, T>>,
    selectedKey: T,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clip(RoundedCornerShape(28.dp))
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    title, 
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options) { (name, id) ->
                        val isSelected = id == selectedKey
                        Surface(
                            onClick = { onSelected(id) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) AccentColor.copy(alpha = 0.1f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AccentColor,
                                        unselectedColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    name, 
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = AccentColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
