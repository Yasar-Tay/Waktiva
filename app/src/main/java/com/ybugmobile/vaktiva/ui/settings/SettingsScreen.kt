package com.ybugmobile.vaktiva.ui.settings

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import com.ybugmobile.vaktiva.ui.settings.composables.*
import com.ybugmobile.vaktiva.ui.theme.dynamicTimeGradient
import com.ybugmobile.vaktiva.utils.PermissionUtils
import java.time.LocalTime
import java.util.Locale as JavaLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAudio: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState(initial = null)
    val prayerDays by viewModel.allPrayerDays.collectAsState()
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showMethodDialog by remember { mutableStateOf(false) }
    var showMadhabDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val backgroundGradient = dynamicTimeGradient(LocalTime.now(), prayerDays)

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
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = backgroundGradient)
                .padding(padding)
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        NotificationSoundSection(
                            settings = settings,
                            onPlayAdhanChange = { viewModel.setPlayAdhanAudio(it) },
                            onNavigateToAudio = onNavigateToAudio
                        )
                        PreferencesSection(
                            settings = settings,
                            onLanguageClick = { showLanguageDialog = true },
                            onMadhabClick = { showMadhabDialog = true },
                            onMethodClick = { showMethodDialog = true }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ReliabilitySection()
                        SettingsSection(title = stringResource(R.string.settings_permissions)) {
                            PermissionManager()
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    NotificationSoundSection(
                        settings = settings,
                        onPlayAdhanChange = { viewModel.setPlayAdhanAudio(it) },
                        onNavigateToAudio = onNavigateToAudio
                    )

                    PreferencesSection(
                        settings = settings,
                        onLanguageClick = { showLanguageDialog = true },
                        onMadhabClick = { showMadhabDialog = true },
                        onMethodClick = { showMethodDialog = true }
                    )

                    ReliabilitySection()

                    SettingsSection(title = stringResource(R.string.settings_permissions)) {
                        PermissionManager()
                    }

                    Spacer(modifier = Modifier.height(32.dp))
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
        onDismissLanguage = { showLanguageDialog = false },
        onDismissMadhab = { showMadhabDialog = false },
        onDismissMethod = { showMethodDialog = false },
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
        onMethodSelected = { viewModel.setCalculationMethod(it); showMethodDialog = false }
    )
}

@Composable
private fun NotificationSoundSection(
    settings: UserSettings?,
    onPlayAdhanChange: (Boolean) -> Unit,
    onNavigateToAudio: () -> Unit
) {
    val context = LocalContext.current
    SettingsSection(title = stringResource(R.string.settings_notifications_sound)) {
        SettingsToggleItem(
            title = stringResource(R.string.settings_play_adhan),
            subtitle = stringResource(R.string.settings_play_adhan_desc),
            icon = Icons.Rounded.VolumeUp,
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
    onMethodClick: () -> Unit
) {
    val currentAppLocales = AppCompatDelegate.getApplicationLocales()
    val currentLanguageCode = if (!currentAppLocales.isEmpty) currentAppLocales.get(0)?.language ?: "system" else "system"

    val madhabOptions = listOf(
        stringResource(R.string.madhab_shafi) to 0,
        stringResource(R.string.madhab_hanafi) to 1
    )

    val methods = getCalculationMethods()

    SettingsSection(title = stringResource(R.string.settings_preferences)) {
        settings?.let { s ->
            SettingsClickItem(
                title = stringResource(R.string.settings_language),
                subtitle = getNativeLanguageName(currentLanguageCode),
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
        }
    }
}

@Composable
private fun ReliabilitySection() {
    val context = LocalContext.current
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(PermissionUtils.isIgnoringBatteryOptimizations(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBatteryOptimizations = PermissionUtils.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsSection(title = stringResource(R.string.settings_reliability)) {
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
    }
}

@Composable
private fun SettingsDialogs(
    settings: UserSettings?,
    showLanguageDialog: Boolean,
    showMadhabDialog: Boolean,
    showMethodDialog: Boolean,
    onDismissLanguage: () -> Unit,
    onDismissMadhab: () -> Unit,
    onDismissMethod: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onMadhabSelected: (Int) -> Unit,
    onMethodSelected: (Int) -> Unit
) {
    val currentAppLocales = AppCompatDelegate.getApplicationLocales()
    val currentLanguageCode = if (!currentAppLocales.isEmpty) currentAppLocales.get(0)?.language ?: "system" else "system"

    if (showLanguageDialog) {
        val languageOptions = listOf(
            getNativeLanguageName("system") to "system",
            getNativeLanguageName("en") to "en",
            getNativeLanguageName("tr") to "tr"
        )
        ModernSelectionDialog(
            title = stringResource(R.string.settings_language),
            options = languageOptions,
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

@Composable
private fun getNativeLanguageName(languageCode: String): String {
    return when (languageCode) {
        "system" -> {
            val systemLocale = ConfigurationCompat.getLocales(Resources.getSystem().configuration).get(0)
            val displayName = systemLocale?.getDisplayName(systemLocale)?.replaceFirstChar { it.uppercase() } ?: ""
            val systemLabel = stringResource(R.string.lang_system)
            if (displayName.isNotEmpty()) "$systemLabel ($displayName)" else systemLabel
        }
        else -> {
            val locale = JavaLocale(languageCode)
            locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
        }
    }
}
