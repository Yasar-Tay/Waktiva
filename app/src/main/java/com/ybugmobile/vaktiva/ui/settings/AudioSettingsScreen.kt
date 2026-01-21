package com.ybugmobile.vaktiva.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ybugmobile.vaktiva.R
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
            TopAppBar(
                title = { Text(stringResource(id = R.string.audio_settings_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.cd_back))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { launcher.launch("audio/*") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(id = R.string.audio_add_custom)) }
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

            // 2. Selection Mode Toggle
            item {
                SelectionModeCard(
                    useSpecific = settings?.useSpecificAdhanForEachPrayer ?: false,
                    onToggle = { viewModel.toggleUseSpecificAdhan(it) }
                )
            }

            // 3. Prayer Selector (Horizontal or Grid) if in specific mode
            if (settings?.useSpecificAdhanForEachPrayer == true) {
                item {
                    Text(
                        text = stringResource(id = R.string.audio_select_prayer_prompt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PrayerTypeSelector(
                        selectedType = selectedPrayerType,
                        onTypeSelected = { viewModel.selectPrayerType(it) }
                    )
                }
            }

            // 4. Audio List Header
            item {
                val headerText = if (settings?.useSpecificAdhanForEachPrayer == true) {
                    stringResource(R.string.audio_header_specific, selectedPrayerType?.displayName ?: stringResource(R.string.audio_header_all_prayers))
                } else {
                    stringResource(R.string.audio_header_global)
                }
                
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

            // 5. Audio Items
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(id = R.string.settings_pre_adhan_warning), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(id = R.string.settings_pre_adhan_warning_summary),
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
                    Text(stringResource(id = R.string.audio_minutes_before), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { onMinutesChange(it.toInt()) },
                        valueRange = 1f..30f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$minutes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
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
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(id = R.string.audio_individual_sounds_title), style = MaterialTheme.typography.titleMedium)
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
fun PrayerTypeSelector(
    selectedType: PrayerType?,
    onTypeSelected: (PrayerType?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PrayerType.entries.filter { it != PrayerType.SUNRISE }.forEach { type ->
            val isSelected = selectedType == type
            FilterChip(
                selected = isSelected,
                onClick = { onTypeSelected(if (isSelected) null else type) },
                label = { Text(type.displayName) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        border = if (item.isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onTogglePreview) {
                Icon(
                    imageVector = if (item.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (item.isPlaying) stringResource(R.string.audio_preview_stop) else stringResource(R.string.audio_preview_play),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Normal
                )
                if (item.isDefault) {
                    Text(
                        text = stringResource(id = R.string.audio_built_in),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (item.isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(id = R.string.audio_selected_description),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.audio_delete_description),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
