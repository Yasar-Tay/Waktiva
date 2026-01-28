package com.ybugmobile.vaktiva.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.utils.PermissionUtils
import java.util.Locale as JavaLocale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAudio: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState(initial = null)
    val context = LocalContext.current
    
    val currentAppLocales = AppCompatDelegate.getApplicationLocales()
    val currentLanguage = if (!currentAppLocales.isEmpty) currentAppLocales.get(0)?.language ?: "system" else "system"

    val scrollState = rememberScrollState()

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

    // Helper to get native display name
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Notifications & Sound Section
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
                        viewModel.setPlayAdhanAudio(enabled)
                    }
                )
                
                SettingsClickItem(
                    title = stringResource(R.string.settings_adhan_sound_selection),
                    subtitle = stringResource(R.string.settings_adhan_sound_selection_desc),
                    icon = Icons.Rounded.MusicNote,
                    onClick = onNavigateToAudio
                )
            }

            // Calculation & Preferences Section
            SettingsSection(title = stringResource(R.string.settings_preferences)) {
                settings?.let { s ->
                    SettingsClickItem(
                        title = stringResource(R.string.settings_language),
                        subtitle = languageOptions.find { it.first == currentLanguage }?.second ?: getNativeLanguageName("system"),
                        icon = Icons.Rounded.Language,
                        onClick = { showLanguageDialog = true }
                    )
                    
                    SettingsClickItem(
                        title = stringResource(R.string.settings_madhab),
                        subtitle = madhabOptions.find { it.second == s.madhab }?.first ?: "",
                        icon = Icons.Rounded.School,
                        onClick = { showMadhabDialog = true }
                    )
                    
                    SettingsClickItem(
                        title = stringResource(R.string.settings_method),
                        subtitle = methods.find { it.second == s.calculationMethod }?.first ?: "",
                        icon = Icons.Rounded.Functions,
                        onClick = { showMethodDialog = true }
                    )
                }
            }

            // Device & Reliability Section
            SettingsSection(title = stringResource(R.string.settings_reliability)) {
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

                SettingsClickItem(
                    title = stringResource(R.string.settings_battery_opt),
                    subtitle = if (isIgnoringBatteryOptimizations) 
                        stringResource(R.string.settings_battery_opt_enabled)
                        else stringResource(R.string.settings_battery_opt_disabled),
                    icon = Icons.Rounded.BatteryChargingFull,
                    iconColor = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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

            // Permissions Section
            SettingsSection(title = stringResource(R.string.settings_permissions)) {
                PermissionManager()
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Dialogs
    if (showLanguageDialog) {
        ModernSelectionDialog(
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
        ModernSelectionDialog(
            title = stringResource(R.string.settings_madhab),
            options = madhabOptions,
            selectedKey = settings?.madhab ?: 0,
            onSelected = { viewModel.setMadhab(it); showMadhabDialog = false },
            onDismiss = { showMadhabDialog = false }
        )
    }

    if (showMethodDialog) {
        ModernSelectionDialog(
            title = stringResource(R.string.settings_method),
            options = methods,
            selectedKey = settings?.calculationMethod ?: 3,
            onSelected = { viewModel.setCalculationMethod(it); showMethodDialog = false },
            onDismiss = { showMethodDialog = false }
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title.toUpperCase(Locale.current),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsClickItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionManager() {
    val context = LocalContext.current
    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        ModernPermissionItem(
            title = stringResource(R.string.settings_location_access),
            isGranted = permissionState.allPermissionsGranted,
            icon = Icons.Rounded.LocationOn
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationPermission = permissionState.permissions.find { it.permission == Manifest.permission.POST_NOTIFICATIONS }
            ModernPermissionItem(
                title = stringResource(R.string.settings_notifications),
                isGranted = notificationPermission?.status?.isGranted == true,
                icon = Icons.Rounded.NotificationsActive
            )
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ModernPermissionItem(
                title = stringResource(R.string.settings_exact_alarm_title),
                isGranted = PermissionUtils.canScheduleExactAlarms(context) ?: false,
                icon = Icons.Rounded.Alarm
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_manage_system), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
fun ModernPermissionItem(title: String, isGranted: Boolean, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, 
            contentDescription = null, 
            tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title, 
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = CircleShape,
            color = if (isGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .widthIn(min = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isGranted) stringResource(R.string.settings_granted) else stringResource(R.string.settings_denied),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ModernSelectionDialog(
    title: String,
    options: List<Pair<String, T>>,
    selectedKey: T,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clip(RoundedCornerShape(28.dp)),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    title, 
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
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
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    name, 
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
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
                    Text(stringResource(android.R.string.cancel), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
