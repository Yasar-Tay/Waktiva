package com.ybugmobile.waktiva.ui.settings

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.local.preferences.UserSettings
import com.ybugmobile.waktiva.ui.settings.composables.*
import com.ybugmobile.waktiva.utils.LanguageUtils
import com.ybugmobile.waktiva.utils.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAudio: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState(initial = null)
    val allDays by viewModel.allPrayerDays.collectAsState()

    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showMethodDialog by remember { mutableStateOf(false) }
    var showMadhabDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDeleteHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        contentWindowInsets = WindowInsets.systemBars
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = 20.dp)
                        .padding(start = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SystemHealthCard(
                            hasPrayerData = allDays.isNotEmpty()
                        )
                        NotificationSoundSection(
                            settings = settings,
                            onPlayAdhanChange = { viewModel.setPlayAdhanAudio(it) },
                            onNavigateToAudio = onNavigateToAudio
                        )
                        PreferencesSection(
                            settings = settings,
                            onLanguageClick = { showLanguageDialog = true },
                            onMadhabClick = { showMadhabDialog = true },
                            onMethodClick = { showMethodDialog = true },
                            onWeatherEffectsChange = { viewModel.setShowWeatherEffects(it) }
                        )
                        Spacer(modifier = Modifier.height(80.dp))
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        DataManagementSection(
                            onDeleteHistoryClick = { showDeleteHistoryDialog = true }
                        )
                        AboutSection(onShowLicensesClick = onNavigateToLicenses)
                        SettingsSection(
                            title = stringResource(R.string.settings_permissions)
                        ) {
                            PermissionManager()
                        }
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    SystemHealthCard(
                        hasPrayerData = allDays.isNotEmpty()
                    )

                    NotificationSoundSection(
                        settings = settings,
                        onPlayAdhanChange = { viewModel.setPlayAdhanAudio(it) },
                        onNavigateToAudio = onNavigateToAudio
                    )

                    PreferencesSection(
                        settings = settings,
                        onLanguageClick = { showLanguageDialog = true },
                        onMadhabClick = { showMadhabDialog = true },
                        onMethodClick = { showMethodDialog = true },
                        onWeatherEffectsChange = { viewModel.setShowWeatherEffects(it) }
                    )

                    DataManagementSection(
                        onDeleteHistoryClick = { showDeleteHistoryDialog = true }
                    )

                    AboutSection(onShowLicensesClick = onNavigateToLicenses)

                    SettingsSection(
                        title = stringResource(R.string.settings_permissions)
                    ) {
                        PermissionManager()
                    }

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Dialogs
    SettingsDialogs(
        settings = settings,
        showLanguageDialog = showLanguageDialog,
        showMadhabDialog = showMadhabDialog,
        showMethodDialog = showMethodDialog,
        showDeleteHistoryDialog = showDeleteHistoryDialog,
        onDismissLanguage = { showLanguageDialog = false },
        onDismissMadhab = { showMadhabDialog = false },
        onDismissMethod = { showMethodDialog = false },
        onDismissDeleteHistory = { showDeleteHistoryDialog = false },
        onLanguageSelected = { lang ->
            val appLocale: LocaleListCompat = if (lang == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(lang)
            }
            AppCompatDelegate.setApplicationLocales(appLocale)
            viewModel.updateLanguage(lang)
            showLanguageDialog = false
        },
        onMadhabSelected = { viewModel.setMadhab(it); showMadhabDialog = false },
        onMethodSelected = { viewModel.setCalculationMethod(it); showMethodDialog = false },
        onDeleteHistoryConfirm = {
            viewModel.deletePastData()
            showDeleteHistoryDialog = false
        }
    )
}

@Composable
private fun NotificationSoundSection(
    settings: UserSettings?,
    onPlayAdhanChange: (Boolean) -> Unit,
    onNavigateToAudio: () -> Unit
) {
    val context = LocalContext.current
    SettingsSection(
        title = stringResource(R.string.settings_notifications_sound)
    ) {
        SettingsToggleItem(
            title = stringResource(R.string.settings_play_adhan),
            subtitle = stringResource(R.string.settings_play_adhan_desc),
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            checked = settings?.playAdhanAudio ?: true,
            onCheckedChange = { enabled ->
                if (enabled && !PermissionUtils.canScheduleExactAlarms(context)) {
                    PermissionUtils.getExactAlarmSettingIntent(context)?.let {
                        context.startActivity(it)
                    }
                }
                onPlayAdhanChange(enabled)
            }
        )

        SettingsClickItem(
            title = stringResource(R.string.settings_adhan_sound_selection),
            subtitle = stringResource(R.string.settings_adhan_sound_selection_desc),
            icon = Icons.Rounded.MusicNote,
            onClick = onNavigateToAudio
        )
    }
}

@Composable
private fun PreferencesSection(
    settings: UserSettings?,
    onLanguageClick: () -> Unit,
    onMadhabClick: () -> Unit,
    onMethodClick: () -> Unit,
    onWeatherEffectsChange: (Boolean) -> Unit
) {
    val currentAppLocales = AppCompatDelegate.getApplicationLocales()
    val currentLanguageCode = if (!currentAppLocales.isEmpty) currentAppLocales.get(0)?.language ?: "system" else "system"

    val madhabOptions = listOf(
        stringResource(R.string.madhab_shafi) to 0,
        stringResource(R.string.madhab_hanafi) to 1
    )

    val methods = getCalculationMethods()

    SettingsSection(
        title = stringResource(R.string.settings_preferences)
    ) {
        settings?.let { s ->
            SettingsClickItem(
                title = stringResource(R.string.settings_language),
                subtitle = LanguageUtils.getNativeLanguageName(currentLanguageCode),
                icon = Icons.Rounded.Language,
                onClick = onLanguageClick
            )

            SettingsClickItem(
                title = stringResource(R.string.settings_madhab),
                subtitle = madhabOptions.find { it.second == s.madhab }?.first ?: "",
                icon = Icons.Rounded.School,
                onClick = onMadhabClick
            )

            SettingsClickItem(
                title = stringResource(R.string.settings_method),
                subtitle = methods.find { it.second == s.calculationMethod }?.first ?: "",
                icon = Icons.Rounded.Functions,
                onClick = onMethodClick
            )

            SettingsToggleItem(
                title = stringResource(R.string.settings_weather_effects),
                subtitle = stringResource(R.string.settings_weather_effects_desc),
                icon = Icons.Rounded.CloudQueue,
                checked = s.showWeatherEffects,
                onCheckedChange = onWeatherEffectsChange
            )
        }
    }
}

@Composable
private fun ReliabilitySection() {
    val context = LocalContext.current
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context))
    }
    var canScheduleExactAlarms by remember {
        mutableStateOf(PermissionUtils.canScheduleExactAlarms(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations = PermissionUtils.isIgnoringBatteryOptimizations(context)
                canScheduleExactAlarms = PermissionUtils.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsSection(
        title = stringResource(R.string.settings_reliability)
    ) {
        // Battery Optimization
        SettingsClickItem(
            title = stringResource(R.string.settings_battery_opt),
            subtitle = if (isIgnoringBatteryOptimizations)
                stringResource(R.string.settings_battery_opt_enabled)
                else stringResource(R.string.settings_battery_opt_disabled),
            icon = Icons.Rounded.BatteryChargingFull,
            iconColor = if (isIgnoringBatteryOptimizations) Color(0xFF4CAF50) else Color(0xFFF87171),
            onClick = {
                try {
                    if (!isIgnoringBatteryOptimizations) {
                        context.startActivity(PermissionUtils.getIgnoreBatteryOptimizationIntent(context))
                    } else {
                        context.startActivity(PermissionUtils.getBatteryOptimizationSettingsIntent())
                    }
                } catch (e: Exception) {
                    try {
                        context.startActivity(PermissionUtils.getBatteryOptimizationSettingsIntent())
                    } catch (e2: Exception) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                }
            }
        )

        // Exact Alarms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingsClickItem(
                title = stringResource(R.string.settings_exact_alarm_title),
                subtitle = if (canScheduleExactAlarms)
                    stringResource(R.string.settings_granted)
                    else stringResource(R.string.settings_exact_alarm_desc),
                icon = Icons.Rounded.Alarm,
                iconColor = if (canScheduleExactAlarms) Color(0xFF4CAF50) else Color(0xFFF87171),
                onClick = {
                    if (!canScheduleExactAlarms) {
                        PermissionUtils.getExactAlarmSettingIntent(context)?.let {
                            context.startActivity(it)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun DataManagementSection(
    onDeleteHistoryClick: () -> Unit
) {
    SettingsSection(
        title = stringResource(R.string.settings_data_management)
    ) {
        SettingsClickItem(
            title = stringResource(R.string.settings_delete_history),
            subtitle = stringResource(R.string.settings_delete_history_desc),
            icon = Icons.Rounded.DeleteSweep,
            onClick = onDeleteHistoryClick
        )
    }
}

@Composable
private fun AboutSection(onShowLicensesClick: () -> Unit) {
    SettingsSection(
        title = "About"
    ) {
        SettingsClickItem(
            title = "Open Source Licenses",
            subtitle = "License details for open source software",
            icon = Icons.Rounded.Description,
            onClick = onShowLicensesClick
        )
    }
}

@Composable
private fun SettingsDialogs(
    settings: UserSettings?,
    showLanguageDialog: Boolean,
    showMadhabDialog: Boolean,
    showMethodDialog: Boolean,
    showDeleteHistoryDialog: Boolean,
    onDismissLanguage: () -> Unit,
    onDismissMadhab: () -> Unit,
    onDismissMethod: () -> Unit,
    onDismissDeleteHistory: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onMadhabSelected: (Int) -> Unit,
    onMethodSelected: (Int) -> Unit,
    onDeleteHistoryConfirm: () -> Unit
) {
    val currentAppLocales = AppCompatDelegate.getApplicationLocales()
    val currentLanguageCode = if (!currentAppLocales.isEmpty) currentAppLocales.get(0)?.language ?: "system" else "system"

    if (showLanguageDialog) {
        ModernSelectionDialog(
            title = stringResource(R.string.settings_language),
            options = LanguageUtils.getLanguageOptions(),
            selectedKey = currentLanguageCode,
            onSelected = onLanguageSelected,
            onDismiss = onDismissLanguage
        )
    }

    if (showMadhabDialog) {
        val madhabOptions = listOf(
            stringResource(R.string.madhab_shafi) to 0,
            stringResource(R.string.madhab_hanafi) to 1
        )
        ModernSelectionDialog(
            title = stringResource(R.string.settings_madhab),
            options = madhabOptions,
            selectedKey = settings?.madhab ?: 0,
            onSelected = onMadhabSelected,
            onDismiss = onDismissMadhab
        )
    }

    if (showMethodDialog) {
        ModernSelectionDialog(
            title = stringResource(R.string.settings_method),
            options = getCalculationMethods(),
            selectedKey = settings?.calculationMethod ?: 3,
            onSelected = onMethodSelected,
            onDismiss = onDismissMethod
        )
    }

    if (showDeleteHistoryDialog) {
        AlertDialog(
            onDismissRequest = onDismissDeleteHistory,
            title = { Text(stringResource(R.string.settings_delete_history_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_history_confirm_desc)) },
            confirmButton = {
                TextButton(
                    onClick = onDeleteHistoryConfirm,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF87171))
                ) {
                    Text(stringResource(R.string.settings_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteHistory) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }
}

@Composable
private fun getCalculationMethods() = listOf(
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
