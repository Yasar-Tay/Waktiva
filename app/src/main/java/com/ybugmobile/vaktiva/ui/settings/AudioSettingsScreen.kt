package com.ybugmobile.vaktiva.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.data.local.preferences.UserSettings
import com.ybugmobile.vaktiva.domain.model.PrayerType

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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        stringResource(id = R.string.audio_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.cd_back))
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { launcher.launch("audio/*") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(id = R.string.audio_add_custom)) },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            // 1. Pre-Adhan Warning Section
            item {
                PreAdhanWarningCard(
                    enabled = settings?.enablePreAdhanWarning ?: true,
                    minutes = settings?.preAdhanWarningMinutes ?: 5,
                    onToggle = { viewModel.togglePreAdhanWarning(it) },
                    onMinutesChange = { viewModel.updatePreAdhanWarningMinutes(it) }
                )
            }

            // 2. Fajr Sunrise Alarm Section
            item {
                FajrSunriseAlarmCard(
                    enabled = settings?.useFajrAlarmBeforeSunrise ?: false,
                    minutes = settings?.fajrAlarmMinutesBeforeSunrise ?: 45,
                    onToggle = { viewModel.toggleUseFajrAlarmBeforeSunrise(it) },
                    onMinutesChange = { viewModel.updateFajrAlarmMinutesBeforeSunrise(it) }
                )
            }

            // 3. Selection Mode Toggle
            item {
                SelectionModeCard(
                    useSpecific = settings?.useSpecificAdhanForEachPrayer ?: false,
                    onToggle = { viewModel.toggleUseSpecificAdhan(it) }
                )
            }

            // 4. Redesigned Prayer Selector
            if (settings?.useSpecificAdhanForEachPrayer == true) {
                item {
                    Column {
                        Text(
                            text = stringResource(id = R.string.audio_select_prayer_prompt),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                        PrayerGridSelector(
                            selectedType = selectedPrayerType,
                            onTypeSelected = { viewModel.selectPrayerType(it) }
                        )
                    }
                }
            }

            // 5. Audio List Header
            item {
                val headerText = if (settings?.useSpecificAdhanForEachPrayer == true) {
                    stringResource(R.string.audio_header_specific, selectedPrayerType?.displayName ?: stringResource(R.string.audio_header_all_prayers))
                } else {
                    stringResource(R.string.audio_header_global)
                }
                
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.audio_supported_formats),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 6. Audio Items
            items(audioItems) { item ->
                AudioFileItem(
                    item = item,
                    onSelect = { viewModel.selectAudio(item.path) },
                    onTogglePreview = { viewModel.togglePreview(item.path) },
                    onDelete = if (!item.isDefault) { { viewModel.deleteAudio(item.path) } } else null
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun PreAdhanWarningCard(
    enabled: Boolean,
    minutes: Int,
    onToggle: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(id = R.string.settings_pre_adhan_warning),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(id = R.string.settings_pre_adhan_warning_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            
            if (enabled) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        stringResource(id = R.string.audio_minutes_before),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { onMinutesChange(it.toInt()) },
                        valueRange = 1f..30f,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$minutes",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FajrSunriseAlarmCard(
    enabled: Boolean,
    minutes: Int,
    onToggle: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Fajr Alarm (Before Sunrise)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Determine Fajr alarm time based on sunrise (except in Ramadan).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            
            if (enabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        stringResource(id = R.string.audio_minutes_before),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { onMinutesChange(it.toInt()) },
                        valueRange = 1f..120f,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$minutes",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelectionModeCard(
    useSpecific: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(id = R.string.audio_individual_sounds_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(id = R.string.audio_individual_sounds_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = useSpecific, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun PrayerGridSelector(
    selectedType: PrayerType?,
    onTypeSelected: (PrayerType?) -> Unit
) {
    val prayers = PrayerType.entries.filter { it != PrayerType.SUNRISE }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Grid with 3 columns: All, Fajr, Dhuhr
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrayerItem(
                name = stringResource(R.string.audio_header_all_prayers),
                isSelected = selectedType == null,
                onClick = { onTypeSelected(null) },
                modifier = Modifier.weight(1f)
            )
            PrayerItem(
                name = PrayerType.FAJR.displayName,
                isSelected = selectedType == PrayerType.FAJR,
                onClick = { onTypeSelected(PrayerType.FAJR) },
                modifier = Modifier.weight(1f)
            )
            PrayerItem(
                name = PrayerType.DHUHR.displayName,
                isSelected = selectedType == PrayerType.DHUHR,
                onClick = { onTypeSelected(PrayerType.DHUHR) },
                modifier = Modifier.weight(1f)
            )
        }
        // Grid with 3 columns: Asr, Maghrib, Isha
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrayerItem(
                name = PrayerType.ASR.displayName,
                isSelected = selectedType == PrayerType.ASR,
                onClick = { onTypeSelected(PrayerType.ASR) },
                modifier = Modifier.weight(1f)
            )
            PrayerItem(
                name = PrayerType.MAGHRIB.displayName,
                isSelected = selectedType == PrayerType.MAGHRIB,
                onClick = { onTypeSelected(PrayerType.MAGHRIB) },
                modifier = Modifier.weight(1f)
            )
            PrayerItem(
                name = PrayerType.ISHA.displayName,
                isSelected = selectedType == PrayerType.ISHA,
                onClick = { onTypeSelected(PrayerType.ISHA) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PrayerItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        label = "bg"
    )
    val contentColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "content"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center
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
    val containerColor by animateColorAsState(
        if (item.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
        label = "containerColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (item.isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onTogglePreview,
                shape = CircleShape,
                color = if (item.isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (item.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (item.isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
                if (item.isDefault) {
                    Text(
                        text = stringResource(id = R.string.audio_built_in),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (item.isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
