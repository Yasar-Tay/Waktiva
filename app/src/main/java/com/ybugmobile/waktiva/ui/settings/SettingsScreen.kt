package com.ybugmobile.waktiva.ui.settings

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.local.preferences.DEFAULT_PRAYER_LOG_REMINDER_MINUTES
import com.ybugmobile.waktiva.data.local.preferences.UserSettings
import com.ybugmobile.waktiva.domain.model.DayCircleStyle
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.prayerlog.TallyIcon
import com.ybugmobile.waktiva.ui.settings.composables.*
import com.ybugmobile.waktiva.ui.theme.GlassSurface
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import com.ybugmobile.waktiva.utils.applyAppLanguage
import com.ybugmobile.waktiva.utils.LanguageUtils
import com.ybugmobile.waktiva.utils.PermissionUtils
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAudio: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToDonate: () -> Unit,
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
    var showClearPrayerLogDialog by remember { mutableStateOf(false) }

    // The prayer log's file goes where the user picks, through the system's own file picker.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportPrayerLog)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importPrayerLog)
    }
    val onExportPrayerLog = { exportLauncher.launch("waktiva-prayer-log-${LocalDate.now()}.json") }
    // Some file managers don't know .json and call it a binary or text file.
    val onImportPrayerLog = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            val message = when (event) {
                SettingsViewModel.UiEvent.PrayerHistoryDeleted -> context.getString(R.string.settings_delete_history_success)
                SettingsViewModel.UiEvent.PrayerHistoryDeleteFailed -> context.getString(R.string.settings_delete_history_error)
                SettingsViewModel.UiEvent.PrayerLogExported -> context.getString(R.string.prayer_log_exported)
                SettingsViewModel.UiEvent.PrayerLogExportFailed -> context.getString(R.string.prayer_log_export_failed)
                is SettingsViewModel.UiEvent.PrayerLogImported -> context.getString(R.string.prayer_log_imported, event.added)
                SettingsViewModel.UiEvent.PrayerLogImportInvalid -> context.getString(R.string.prayer_log_import_invalid)
                SettingsViewModel.UiEvent.PrayerLogImportFailed -> context.getString(R.string.prayer_log_import_failed)
                SettingsViewModel.UiEvent.PrayerLogCleared -> context.getString(R.string.prayer_log_cleared)
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
                        SupportCard(onClick = onNavigateToDonate)
                        SystemHealthCard(
                            hasPrayerData = allDays.isNotEmpty()
                        )
                        AppearanceSection(
                            settings = settings,
                            onLanguageClick = { showLanguageDialog = true },
                            onDayCircleClick = { showDayCircleDialog = true },
                            onWeatherEffectsChange = { viewModel.setShowWeatherEffects(it) }
                        )
                        NotificationSoundSection(
                            settings = settings,
                            onPlayAdhanChange = { viewModel.setPlayAdhanAudio(it) },
                            onPlayAdhanDuaChange = { viewModel.setPlayAdhanDua(it) },
                            onPrayerAdhanToggle = { type, enabled -> viewModel.setPrayerAdhanEnabled(type, enabled) },
                            onSilentNotificationChange = { viewModel.setSilentPrayerNotification(it) },
                            onNavigateToAudio = onNavigateToAudio
                        )
                        PrayerTimesSection(
                            settings = settings,
                            onMethodClick = { showMethodDialog = true },
                            onMadhabClick = { showMadhabDialog = true }
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
                        PrayerLogSection(
                            settings = settings,
                            onEnabledChange = { viewModel.setPrayerLogEnabled(it) },
                            onReminderChange = { viewModel.setPrayerLogReminder(it) },
                            onReminderMinutesChange = { viewModel.setPrayerLogReminderMinutes(it) },
                            onGameNotificationsChange = { viewModel.setPrayerLogGameNotifications(it) },
                            onExport = onExportPrayerLog,
                            onImport = onImportPrayerLog,
                            onClear = { showClearPrayerLogDialog = true }
                        )
                        PermissionsSection()
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

                    // Supporting the app leads, then any system issue to fix. Then what the app looks like and
                    // how it announces the prayers, the prayer times themselves, the data it keeps, the prayer
                    // log; the permissions and the app's own details come last.
                    SupportCard(onClick = onNavigateToDonate)

                    SystemHealthCard(
                        hasPrayerData = allDays.isNotEmpty()
                    )

                    AppearanceSection(
                        settings = settings,
                        onLanguageClick = { showLanguageDialog = true },
                        onDayCircleClick = { showDayCircleDialog = true },
                        onWeatherEffectsChange = { viewModel.setShowWeatherEffects(it) }
                    )

                    NotificationSoundSection(
                        settings = settings,
                        onPlayAdhanChange = { viewModel.setPlayAdhanAudio(it) },
                        onPlayAdhanDuaChange = { viewModel.setPlayAdhanDua(it) },
                        onPrayerAdhanToggle = { type, enabled -> viewModel.setPrayerAdhanEnabled(type, enabled) },
                        onSilentNotificationChange = { viewModel.setSilentPrayerNotification(it) },
                        onNavigateToAudio = onNavigateToAudio
                    )

                    PrayerTimesSection(
                        settings = settings,
                        onMethodClick = { showMethodDialog = true },
                        onMadhabClick = { showMadhabDialog = true }
                    )

                    DataManagementSection(
                        onDeleteHistoryClick = { showDeleteHistoryDialog = true }
                    )

                    PrayerLogSection(
                        settings = settings,
                        onEnabledChange = { viewModel.setPrayerLogEnabled(it) },
                        onReminderChange = { viewModel.setPrayerLogReminder(it) },
                        onReminderMinutesChange = { viewModel.setPrayerLogReminderMinutes(it) },
                        onGameNotificationsChange = { viewModel.setPrayerLogGameNotifications(it) },
                        onExport = onExportPrayerLog,
                        onImport = onImportPrayerLog,
                        onClear = { showClearPrayerLogDialog = true }
                    )

                    PermissionsSection()

                    AboutSection(onShowLicensesClick = onNavigateToLicenses)

                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Dialogs
    if (showClearPrayerLogDialog) {
        AlertDialog(
            onDismissRequest = { showClearPrayerLogDialog = false },
            title = { Text(stringResource(R.string.prayer_log_clear_confirm_title)) },
            text = { Text(stringResource(R.string.prayer_log_clear_confirm_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearPrayerLog()
                        showClearPrayerLogDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF87171))
                ) {
                    Text(stringResource(R.string.prayer_log_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPrayerLogDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

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

/** The way to the donation screen: a card in the glass, warmed with the heart's rose. */
@Composable
private fun SupportCard(onClick: () -> Unit) {
    val glassTheme = LocalGlassTheme.current
    GlassSurface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        tint = SupportRose,
        accent = SupportRose.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(listOf(SupportRose, Color(0xFFFB923C))),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.donate_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = glassTheme.contentColor
                )
                Text(
                    text = stringResource(R.string.settings_support_desc),
                    style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.3.sp),
                    color = glassTheme.contentColor.copy(alpha = 0.75f)
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = glassTheme.contentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private val SupportRose = Color(0xFFF87171)

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

/**
 * Whether the prayer log (çetele) is on, and while it is, the reminder after Isha to mark the
 * day's prayers. Off, the navigation bar carries the donate tab in its place and the day circle's
 * badges only show their times. The log can be exported to a file, imported from one, or cleared,
 * whether it is on or not.
 */
@Composable
private fun PrayerLogSection(
    settings: UserSettings?,
    onEnabledChange: (Boolean) -> Unit,
    onReminderChange: (Boolean) -> Unit,
    onReminderMinutesChange: (Int) -> Unit,
    onGameNotificationsChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClear: () -> Unit
) {
    var showReminderTimeDialog by remember { mutableStateOf(false) }
    val reminderOptions = PrayerLogReminderMinutes.map {
        stringResource(R.string.prayer_log_reminder_after, it) to it
    }

    SettingsSection(
        title = stringResource(R.string.prayer_log_title)
    ) {
        settings?.let { s ->
            SettingsToggleItem(
                title = stringResource(R.string.prayer_log_setting),
                subtitle = stringResource(R.string.prayer_log_setting_desc),
                icon = TallyIcon,
                checked = s.prayerLogEnabled,
                onCheckedChange = onEnabledChange
            )

            if (s.prayerLogEnabled) {
                SettingsToggleItem(
                    title = stringResource(R.string.prayer_log_reminder),
                    subtitle = stringResource(R.string.prayer_log_reminder_desc),
                    icon = Icons.Rounded.NotificationsActive,
                    checked = s.prayerLogReminderEnabled,
                    onCheckedChange = onReminderChange
                )

                if (s.prayerLogReminderEnabled) {
                    SettingsClickItem(
                        title = stringResource(R.string.prayer_log_reminder_time),
                        subtitle = stringResource(R.string.prayer_log_reminder_after, s.prayerLogReminderMinutes),
                        icon = Icons.Rounded.Schedule,
                        onClick = { showReminderTimeDialog = true }
                    )
                }

                SettingsToggleItem(
                    title = stringResource(R.string.prayer_log_game_setting),
                    subtitle = stringResource(R.string.prayer_log_game_setting_desc),
                    icon = Icons.Rounded.EmojiEvents,
                    checked = s.prayerLogGameNotifications,
                    onCheckedChange = onGameNotificationsChange
                )
            }
        }

        SettingsClickItem(
            title = stringResource(R.string.prayer_log_export),
            subtitle = stringResource(R.string.prayer_log_export_desc),
            icon = Icons.Rounded.FileUpload,
            onClick = onExport
        )
        SettingsClickItem(
            title = stringResource(R.string.prayer_log_import),
            subtitle = stringResource(R.string.prayer_log_import_desc),
            icon = Icons.Rounded.FileDownload,
            onClick = onImport
        )
        SettingsClickItem(
            title = stringResource(R.string.prayer_log_clear),
            subtitle = stringResource(R.string.prayer_log_clear_desc),
            icon = Icons.Rounded.DeleteForever,
            onClick = onClear
        )
    }

    if (showReminderTimeDialog) {
        ModernSelectionDialog(
            title = stringResource(R.string.prayer_log_reminder_time),
            options = reminderOptions,
            selectedKey = settings?.prayerLogReminderMinutes ?: DEFAULT_PRAYER_LOG_REMINDER_MINUTES,
            onSelected = {
                onReminderMinutesChange(it)
                showReminderTimeDialog = false
            },
            onDismiss = { showReminderTimeDialog = false }
        )
    }
}

/** How long after Isha the prayer log reminder can come, in minutes. */
private val PrayerLogReminderMinutes = listOf(30, 60, 90, 120)

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
    stringResource(R.string.day_circle_style_steel) to DayCircleStyle.STEEL
)

@Composable
private fun dayCircleStyleDescription(style: DayCircleStyle) = stringResource(
    when (style) {
        DayCircleStyle.CLASSIC -> R.string.day_circle_style_classic_desc
        DayCircleStyle.BRASS -> R.string.day_circle_style_brass_desc
        DayCircleStyle.STEEL -> R.string.day_circle_style_steel_desc
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
