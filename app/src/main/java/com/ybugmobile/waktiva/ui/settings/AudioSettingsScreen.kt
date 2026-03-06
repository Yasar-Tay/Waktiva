package com.ybugmobile.waktiva.ui.settings

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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.domain.model.PrayerType
import com.ybugmobile.waktiva.ui.settings.composables.SettingsToggleItem
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(
    viewModel: AudioSettingsViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null
) {
    val audioItems by viewModel.audioItems.collectAsState()
    val prayerDays by viewModel.allPrayerDays.collectAsState()
    val settingsState by viewModel.settings.collectAsState(initial = null)
    val settings = settingsState
    val selectedPrayerType by viewModel.selectedPrayerType.collectAsState()

    val todayPrayerDay = remember(prayerDays) {
        val today = java.time.LocalDate.now()
        prayerDays.find { it.date == today }
    }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.addCustomAudio(it) }
    }

    val glassTheme = LocalGlassTheme.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current

    var showSelectionDialog by remember { mutableStateOf(false) }

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
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                        
                        settings?.let { currentSettings ->
                            if (currentSettings.useSpecificAdhanForEachPrayer) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color.White.copy(alpha = 0.1f)
                                )
                                Spacer(Modifier.height(8.dp))

                                PrayerType.entries.filter { it != PrayerType.SUNRISE }.forEach { prayer ->
                                    val selectedAdhanPath = currentSettings.prayerSpecificAdhanPaths[prayer]
                                        ?: currentSettings.selectedAdhanPath

                                    val adhanItem = audioItems.find { it.path == selectedAdhanPath }
                                        ?: audioItems.find { it.isDefault && selectedAdhanPath == null }
                                        ?: audioItems.find { it.isDefault }

                                    PrayerAdhanRow(
                                        prayerName = prayer.getDisplayName(context),
                                        adhanTitle = adhanItem?.name ?: "",
                                        adhanArtist = adhanItem?.artist,
                                        onClick = {
                                            viewModel.selectPrayerType(prayer)
                                            showSelectionDialog = true
                                        }
                                    )
                                }
                            } else {
                                // Global Selection Row
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color.White.copy(alpha = 0.1f)
                                )
                                Spacer(Modifier.height(8.dp))

                                val adhanItem = audioItems.find { it.isSelected }
                                    ?: audioItems.find { it.isDefault }

                                PrayerAdhanRow(
                                    prayerName = stringResource(R.string.audio_header_all_prayers),
                                    adhanTitle = adhanItem?.name ?: "",
                                    adhanArtist = adhanItem?.artist,
                                    onClick = {
                                        viewModel.selectPrayerType(null)
                                        showSelectionDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(100.dp)) }
            }

            if (showSelectionDialog) {
                AdhanSelectionDialog(
                    title = selectedPrayerType?.getDisplayName(context) ?: stringResource(R.string.audio_header_all_prayers),
                    audioItems = audioItems,
                    onSelect = { 
                        viewModel.selectAudio(it)
                        showSelectionDialog = false
                    },
                    onTogglePreview = { viewModel.togglePreview(it) },
                    onDelete = { viewModel.deleteAudio(it) },
                    onAddCustom = { launcher.launch("audio/*") },
                    onDismiss = { 
                        viewModel.selectPrayerType(null)
                        showSelectionDialog = false 
                    }
                )
            }
        }
    }
}

@Composable
private fun PrayerAdhanRow(
    prayerName: String,
    adhanTitle: String,
    adhanArtist: String?,
    onClick: () -> Unit
) {
    val glassTheme = LocalGlassTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = prayerName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (!adhanArtist.isNullOrEmpty()) "$adhanTitle • $adhanArtist" else adhanTitle,
                style = MaterialTheme.typography.labelMedium,
                color = glassTheme.secondaryContentColor.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = glassTheme.secondaryContentColor.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdhanSelectionDialog(
    title: String,
    audioItems: List<AdhanAudioItem>,
    onSelect: (String) -> Unit,
    onTogglePreview: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    val glassTheme = LocalGlassTheme.current
    var audioToDelete by remember { mutableStateOf<AdhanAudioItem?>(null) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111827),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                TextButton(
                    onClick = onAddCustom,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Rounded.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = glassTheme.contentColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.audio_add_custom).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = glassTheme.contentColor,
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(audioItems) { item ->
                    AudioFileItem(
                        item = item,
                        onSelect = { onSelect(item.path) },
                        onTogglePreview = { onTogglePreview(item.path) },
                        onDelete = if (!item.isDefault) { { audioToDelete = item } } else null
                    )
                }
            }
        }
    }

    if (audioToDelete != null) {
        AlertDialog(
            onDismissRequest = { audioToDelete = null },
            title = { Text(stringResource(R.string.audio_delete_confirm)) },
            text = { 
                Text(
                    text = "${stringResource(R.string.audio_delete_description)}\n\n\"${audioToDelete?.name}\""
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        onDelete(audioToDelete!!.path)
                        audioToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF87171))
                ) {
                    Text(stringResource(R.string.settings_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { audioToDelete = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
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
        color = if (item.isSelected) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, if (item.isSelected) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f))
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
                if (!item.artist.isNullOrEmpty()) {
                    Text(
                        text = item.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = glassTheme.secondaryContentColor,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                } else if (item.isDefault) {
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
