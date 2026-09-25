package com.ybugmobile.waktiva.ui.settings

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.local.preferences.UserSettings
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.settings.composables.*
import com.ybugmobile.waktiva.utils.applyAppLanguage
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
    val context = LocalContext.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showMethodDialog by remember { mutableStateOf(false) }
    var showDayCircleDialog by remember { mutableStateOf(false) }
    var showMadhabDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDeleteHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            val message = when (event) {
                SettingsViewModel.UiEvent.PrayerHistoryDeleted -> R.string.settings_delete_history_success
                SettingsViewModel.UiEvent.PrayerHistoryDeleteFailed -> R.string.settings_delete_history_error
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

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
                        .padding(start = 52.dp),
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
                        PrayerTimesSection(
                            settings = settings,
                            onMethodClick = { showMethodDialog = true },
                            onMadhabClick = { showMadhabDialog = true }
                        )
                        NotificationSoundSection(
                            settings = settings,
                            onPlayAdhanChange = { viewModel.setPlayAdhanAudio(it) },
                            onPlayAdhanDuaChange = { viewModel.setPlayAdhanDua(it) },
                            onPrayerAdhanToggle = { type, enabled -> viewModel.setPrayerAdhanEnabled(type, enabled) },
                            onSilentNotificationChange = { viewModel.setSilentPrayerNotification(it) },
                            onNavigateToAudio = onNavigateToAudio
                        )
                        Spacer(modifier = Modifier.height(80.dp))
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        PermissionsSection()
                        AppearanceSection(
                            settings = settings,
                            onLanguageClick = { showLanguageDialog = true },
                            onDayCircleClick = { showDayCircleDialog = true },
                            onWeatherEffectsChange = { viewModel.setShowWeatherEffects(it) }
                        )
                        DataManagementSection(
                            onDeleteHistoryClick = { showDeleteHistoryDialog = true }
                        )
                        AboutSection(onShowLicensesClick = onNavigateToLicenses)
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

                    // Most used and most consequential first: what sets the prayer times, then how
                    // they are announced and what that needs, then the look of the app, then the rest.
                    SystemHealthCard(
                        hasPrayerData = allDays.isNotEmpty()
                    )

                    PrayerTimesSection(
                        settings = settings,
                        onMethodClick = { showMethodDialog = true },
                        onMadhabClick = { showMadhabDialog = true }
                    )

                    NotificationSoundSection(
                        settings = settings,
                        onPlayAdhanChange = { viewModel.setPlayAdhanAudio(it) },
                        onPlayAdhanDuaChange = { viewModel.setPlayAdhanDua(it) },
                        onPrayerAdhanToggle = { type, enabled -> viewModel.setPrayerAdhanEnabled(type, enabled) },
                        onSilentNotificationChange = { viewModel.setSilentPrayerNotification(it) },
                        onNavigateToAudio = onNavigateToAudio
                    )

                    PermissionsSection()

                    AppearanceSection(
                        settings = settings,
                        onLanguageClick = { showLanguageDialog = true },
                        onDayCircleClick = { showDayCircleDialog = true },
                        onWeatherEffectsChange = { viewModel.setShowWeatherEffects(it) }
                    )

                    DataManagementSection(
                        onDeleteHistoryClick = { showDeleteHistoryDialog = true }
                    )

                    AboutSection(onShowLicensesClick = onNavigateToLicenses)

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
        showDayCircleDialog = showDayCircleDialog,
        showDeleteHistoryDialog = showDeleteHistoryDialog,
        onDismissLanguage = { showLanguageDialog = false },
        onDismissMadhab = { showMadhabDialog = false },
        onDismissMethod = { showMethodDialog = false },
        onDismissDayCircle = { showDayCircleDialog = false },
        onDismissDeleteHistory = { showDeleteHistoryDialog = false },
        onLanguageSelected = { lang ->
            viewModel.updateLanguage(lang) {
                showLanguageDialog = false
                applyAppLanguage(context, lang)
            }
        },
        onMadhabSelected = { viewModel.setMadhab(it); showMadhabDialog = false },
        onMethodSelected = { viewModel.setCalculationMethod(it); showMethodDialog = false },
        onDayCircleSelected = { viewModel.setDayCircleStyle(it); showDayCircleDialog = false },
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
    onPlayAdhanDuaChange: (Boolean) -> Unit,
    onPrayerAdhanToggle: (PrayerType, Boolean) -> Unit,
    onSilentNotificationChange: (Boolean) -> Unit,
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

        if (settings?.playAdhanAudio == true) {
            AdhanPrayerSelectionItem(
                title = stringResource(R.string.settings_adhan_prayers),
                subtitle = stringResource(R.string.settings_adhan_prayers_desc),
                disabledPrayers = settings.adhanDisabledPrayers,
                onPrayerToggle = onPrayerAdhanToggle
            )

            SettingsToggleItem(
                title = stringResource(R.string.settings_play_adhan_dua),
                subtitle = stringResource(R.string.settings_play_adhan_dua_desc),
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                checked = settings.playAdhanDua,
                onCheckedChange = onPlayAdhanDuaChange
            )
        }

        SettingsClickItem(
            title = stringResource(R.string.settings_adhan_sound_selection),
            subtitle = stringResource(R.string.settings_adhan_sound_selection_desc),
            icon = Icons.Rounded.MusicNote,
            onClick = onNavigateToAudio
        )

        // Silent notifications apply whenever at least one prayer won't play the adhan.
        if (settings != null && (!settings.playAdhanAudio || settings.adhanDisabledPrayers.isNotEmpty())) {
            SettingsToggleItem(
                title = stringResource(R.string.settings_silent_prayer_notification),
                subtitle = stringResource(R.string.settings_silent_prayer_notification_desc),
                icon = Icons.Rounded.Notifications,
                checked = settings.showSilentPrayerNotification,
                onCheckedChange = onSilentNotificationChange
            )
        }
    }
}

/** What decides the prayer times themselves: the calculation method and the madhab for Asr. */
@Composable
private fun PrayerTimesSection(
    settings: UserSettings?,
    onMethodClick: () -> Unit,
    onMadhabClick: () -> Unit
) {
    val madhabOptions = listOf(
        stringResource(R.string.madhab_shafi) to 0,
        stringResource(R.string.madhab_hanafi) to 1
    )
    val methods = getCalculationMethods()

    SettingsSection(
        title = stringResource(R.string.settings_section_prayer_times)
    ) {
        settings?.let { s ->
            SettingsClickItem(
                title = stringResource(R.string.settings_method),
                subtitle = methods.find { it.second == s.calculationMethod }?.first ?: "",
                icon = Icons.Rounded.Functions,
                onClick = onMethodClick
            )

            SettingsClickItem(
                title = stringResource(R.string.settings_madhab),
                subtitle = madhabOptions.find { it.second == s.madhab }?.first ?: "",
                icon = Icons.Rounded.School,
                onClick = onMadhabClick
            )
        }
    }
}

/** How the app looks and reads: its language, the day circle's style and the weather effects. */
@Composable
private fun AppearanceSection(
    settings: UserSettings?,
    onLanguageClick: () -> Unit,
    onDayCircleClick: () -> Unit,
    onWeatherEffectsChange: (Boolean) -> Unit
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_appearance)
    ) {
        settings?.let { s ->
            SettingsClickItem(
                title = stringResource(R.string.settings_language),
                subtitle = LanguageUtils.getNativeLanguageName(s.language),
                icon = Icons.Rounded.Language,
                onClick = onLanguageClick
            )

            SettingsClickItem(
                title = stringResource(R.string.settings_day_circle_style),
                subtitle = dayCircleStyleOptions().first { it.second == s.dayCircleStyle }.first,
                icon = Icons.Rounded.Palette,
                onClick = onDayCircleClick
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

/** The permissions the adhan and the prayer times depend on. */
@Composable
private fun PermissionsSection() {
    SettingsSection(
        title = stringResource(R.string.settings_permissions)
    ) {
        PermissionManager()
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
        title = stringResource(R.string.settings_about)
    ) {
        SettingsClickItem(
            title = stringResource(R.string.licenses_title),
            subtitle = stringResource(R.string.settings_licenses_desc),
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
    showDayCircleDialog: Boolean,
    showDeleteHistoryDialog: Boolean,
    onDismissLanguage: () -> Unit,
    onDismissMadhab: () -> Unit,
    onDismissMethod: () -> Unit,
    onDismissDayCircle: () -> Unit,
    onDismissDeleteHistory: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onMadhabSelected: (Int) -> Unit,
    onMethodSelected: (Int) -> Unit,
    onDayCircleSelected: (DayCircleStyle) -> Unit,
    onDeleteHistoryConfirm: () -> Unit
) {
    val currentLanguageCode = settings?.language ?: "system"

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
            optionDescription = { value ->
                if (value == 1) stringResource(R.string.madhab_hanafi_desc) else null
            },
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

    if (showDayCircleDialog) {
        ModernSelectionDialog(
            title = stringResource(R.string.settings_day_circle_style),
            options = dayCircleStyleOptions(),
            selectedKey = settings?.dayCircleStyle ?: DayCircleStyle.DEFAULT,
            optionDescription = { dayCircleStyleDescription(it) },
            onSelected = onDayCircleSelected,
            onDismiss = onDismissDayCircle
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
private fun dayCircleStyleOptions() = listOf(
    stringResource(R.string.day_circle_style_classic) to DayCircleStyle.CLASSIC,
    stringResource(R.string.day_circle_style_brass) to DayCircleStyle.BRASS,
    stringResource(R.string.day_circle_style_steel) to DayCircleStyle.STEEL,
    stringResource(R.string.day_circle_style_skeleton) to DayCircleStyle.SKELETON
)

@Composable
private fun dayCircleStyleDescription(style: DayCircleStyle) = stringResource(
    when (style) {
        DayCircleStyle.CLASSIC -> R.string.day_circle_style_classic_desc
        DayCircleStyle.BRASS -> R.string.day_circle_style_brass_desc
        DayCircleStyle.STEEL -> R.string.day_circle_style_steel_desc
        DayCircleStyle.SKELETON -> R.string.day_circle_style_skeleton_desc
    }
)

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
