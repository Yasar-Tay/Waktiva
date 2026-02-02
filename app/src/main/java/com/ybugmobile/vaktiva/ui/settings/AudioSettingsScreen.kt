package com.ybugmobile.vaktiva.ui.settings

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.domain.model.PrayerType
import com.ybugmobile.vaktiva.ui.settings.composables.SettingsToggleItem
import com.ybugmobile.vaktiva.ui.theme.LocalGlassTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(
    viewModel: AudioSettingsViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null
) {
    val audioItems by viewModel.audioItems.collectAsState()
    val settings by viewModel.settings.collectAsState(initial = null)
    val selectedPrayerType by viewModel.selectedPrayerType.collectAsState()
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.addCustomAudio(it) }
    }

    val glassTheme = LocalGlassTheme.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        stringResource(id = R.string.audio_settings_title).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White
                    ) 
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.cd_back), tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { launcher.launch("audio/*") },
                shape = CircleShape,
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(id = R.string.audio_add_custom).uppercase(), fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left Side: Settings
                    LazyColumn(
                        modifier = Modifier.weight(1.2f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp)
                    ) {
                        item {
                            SettingsCard(
                                title = stringResource(id = R.string.settings_pre_adhan_warning).uppercase()
                            ) {
                                PreAdhanContent(
                                    enabled = settings?.enablePreAdhanWarning ?: true,
                                    minutes = settings?.preAdhanWarningMinutes ?: 5,
                                    onToggle = { viewModel.togglePreAdhanWarning(it) },
                                    onMinutesChange = { viewModel.updatePreAdhanWarningMinutes(it) }
                                )
                            }
                        }

                        item {
                            SettingsCard(
                                title = stringResource(R.string.audio_fajr_sunrise_alarm).uppercase()
                            ) {
                                FajrSunriseContent(
                                    enabled = settings?.useFajrAlarmBeforeSunrise ?: false,
                                    minutes = settings?.fajrAlarmMinutesBeforeSunrise ?: 45,
                                    onToggle = { viewModel.toggleUseFajrAlarmBeforeSunrise(it) },
                                    onMinutesChange = { viewModel.updateFajrAlarmMinutesBeforeSunrise(it) }
                                )
                            }
                        }

                        item {
                            SettingsCard(
                                title = stringResource(id = R.string.audio_individual_sounds_title).uppercase()
                            ) {
                                SelectionModeContent(
                                    useSpecific = settings?.useSpecificAdhanForEachPrayer ?: false,
                                    onToggle = { viewModel.toggleUseSpecificAdhan(it) }
                                )
                            }
                        }

                        if (settings?.useSpecificAdhanForEachPrayer == true) {
                            item {
                                Column {
                                    Text(
                                        text = stringResource(id = R.string.audio_select_prayer_prompt).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = glassTheme.secondaryContentColor,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                                    )
                                    PrayerGridSelector(
                                        selectedType = selectedPrayerType,
                                        onTypeSelected = { viewModel.selectPrayerType(it) }
                                    )
                                }
                            }
                        }
                    }

                    // Right Side: Audio List
                    Column(modifier = Modifier.weight(1f)) {
                        val headerText = if (settings?.useSpecificAdhanForEachPrayer == true) {
                            stringResource(R.string.audio_header_specific, selectedPrayerType?.displayName ?: stringResource(R.string.audio_header_all_prayers))
                        } else {
                            stringResource(R.string.audio_header_global)
                        }
                        
                        Text(
                            text = headerText.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = glassTheme.secondaryContentColor,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 28.dp, start = 8.dp, bottom = 12.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(audioItems) { item ->
                                AudioFileItem(
                                    item = item,
                                    onSelect = { viewModel.selectAudio(item.path) },
                                    onTogglePreview = { viewModel.togglePreview(item.path) },
                                    onDelete = if (!item.isDefault) { { viewModel.deleteAudio(item.path) } } else null
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(20.dp)
                ) {
                    item {
                        SettingsCard(
                            title = stringResource(id = R.string.settings_pre_adhan_warning).uppercase()
                        ) {
                            PreAdhanContent(
                                enabled = settings?.enablePreAdhanWarning ?: true,
                                minutes = settings?.preAdhanWarningMinutes ?: 5,
                                onToggle = { viewModel.togglePreAdhanWarning(it) },
                                onMinutesChange = { viewModel.updatePreAdhanWarningMinutes(it) }
                            )
                        }
                    }

                    item {
                        SettingsCard(
                            title = stringResource(R.string.audio_fajr_sunrise_alarm).uppercase()
                        ) {
                            FajrSunriseContent(
                                enabled = settings?.useFajrAlarmBeforeSunrise ?: false,
                                minutes = settings?.fajrAlarmMinutesBeforeSunrise ?: 45,
                                onToggle = { viewModel.toggleUseFajrAlarmBeforeSunrise(it) },
                                onMinutesChange = { viewModel.updateFajrAlarmMinutesBeforeSunrise(it) }
                            )
                        }
                    }

                    item {
                        SettingsCard(
                            title = stringResource(id = R.string.audio_individual_sounds_title).uppercase()
                        ) {
                            SelectionModeContent(
                                useSpecific = settings?.useSpecificAdhanForEachPrayer ?: false,
                                onToggle = { viewModel.toggleUseSpecificAdhan(it) }
                            )
                        }
                    }

                    if (settings?.useSpecificAdhanForEachPrayer == true) {
                        item {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.audio_select_prayer_prompt).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = glassTheme.secondaryContentColor,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                                )
                                PrayerGridSelector(
                                    selectedType = selectedPrayerType,
                                    onTypeSelected = { viewModel.selectPrayerType(it) }
                                )
                            }
                        }
                    }

                    item {
                        val headerText = if (settings?.useSpecificAdhanForEachPrayer == true) {
                            stringResource(R.string.audio_header_specific, selectedPrayerType?.displayName ?: stringResource(R.string.audio_header_all_prayers))
                        } else {
                            stringResource(R.string.audio_header_global)
                        }
                        
                        Column(modifier = Modifier.padding(top = 16.dp, start = 8.dp)) {
                            Text(
                                text = headerText.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = glassTheme.secondaryContentColor,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    items(audioItems) { item ->
                        AudioFileItem(
                            item = item,
                            onSelect = { viewModel.selectAudio(item.path) },
                            onTogglePreview = { viewModel.togglePreview(item.path) },
                            onDelete = if (!item.isDefault) { { viewModel.deleteAudio(item.path) } } else null
                        )
                    }

                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String, 
    content: @Composable ColumnScope.() -> Unit
) {
    val glassTheme = LocalGlassTheme.current
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = glassTheme.secondaryContentColor,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = glassTheme.containerColor,
            border = BorderStroke(1.dp, glassTheme.borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun PreAdhanContent(
    enabled: Boolean,
    minutes: Int,
    onToggle: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    Column {
        SettingsToggleItem(
            title = stringResource(id = R.string.settings_pre_adhan_warning),
            subtitle = stringResource(id = R.string.settings_pre_adhan_warning_summary),
            icon = Icons.Rounded.NotificationsActive,
            checked = enabled,
            onCheckedChange = onToggle
        )
        
        if (enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            SliderWithLabel(
                label = stringResource(id = R.string.audio_minutes_before),
                value = minutes,
                onValueChange = onMinutesChange,
                valueRange = 1f..30f,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun FajrSunriseContent(
    enabled: Boolean,
    minutes: Int,
    onToggle: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    Column {
        SettingsToggleItem(
            title = stringResource(R.string.audio_fajr_sunrise_alarm),
            subtitle = stringResource(R.string.audio_fajr_sunrise_alarm_desc),
            icon = Icons.Rounded.WbTwilight,
            checked = enabled,
            onCheckedChange = onToggle
        )
        
        if (enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            SliderWithLabel(
                label = stringResource(id = R.string.audio_minutes_before),
                value = minutes,
                onValueChange = onMinutesChange,
                valueRange = 1f..120f,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SliderWithLabel(
    label: String, 
    value: Int, 
    onValueChange: (Int) -> Unit, 
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
            Text("$value", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
fun SelectionModeContent(
    useSpecific: Boolean,
    onToggle: (Boolean) -> Unit
) {
    SettingsToggleItem(
        title = stringResource(id = R.string.audio_individual_sounds_title),
        subtitle = stringResource(id = R.string.audio_individual_sounds_desc),
        icon = Icons.Rounded.LibraryMusic,
        checked = useSpecific,
        onCheckedChange = onToggle
    )
}

@Composable
fun PrayerGridSelector(
    selectedType: PrayerType?,
    onTypeSelected: (PrayerType?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrayerItem(stringResource(R.string.audio_header_all_prayers), selectedType == null, { onTypeSelected(null) }, Modifier.weight(1f))
            PrayerItem(PrayerType.FAJR.displayName, selectedType == PrayerType.FAJR, { onTypeSelected(PrayerType.FAJR) }, Modifier.weight(1f))
            PrayerItem(PrayerType.DHUHR.displayName, selectedType == PrayerType.DHUHR, { onTypeSelected(PrayerType.DHUHR) }, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrayerItem(PrayerType.ASR.displayName, selectedType == PrayerType.ASR, { onTypeSelected(PrayerType.ASR) }, Modifier.weight(1f))
            PrayerItem(PrayerType.MAGHRIB.displayName, selectedType == PrayerType.MAGHRIB, { onTypeSelected(PrayerType.MAGHRIB) }, Modifier.weight(1f))
            PrayerItem(PrayerType.ISHA.displayName, selectedType == PrayerType.ISHA, { onTypeSelected(PrayerType.ISHA) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PrayerItem(name: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, if (isSelected) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun AudioFileItem(
    item: AdhanAudioItem,
    onSelect: () -> Unit,
    onTogglePreview: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val glassTheme = LocalGlassTheme.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        shape = RoundedCornerShape(20.dp),
        color = if (item.isSelected) glassTheme.containerColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, if (item.isSelected) glassTheme.borderColor else Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onTogglePreview,
                shape = CircleShape,
                color = if (item.isPlaying) glassTheme.contentColor else Color.White.copy(alpha = 0.1f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (item.isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = if (item.isPlaying) Color.Black else glassTheme.contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isSelected) FontWeight.Black else FontWeight.Bold,
                    color = if (item.isSelected) glassTheme.contentColor else glassTheme.contentColor.copy(alpha = 0.7f),
                    maxLines = 1
                )
                if (item.isDefault) {
                    Text(
                        text = stringResource(id = R.string.audio_built_in).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = glassTheme.secondaryContentColor,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            if (item.isSelected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = Color(0xFFF87171).copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
