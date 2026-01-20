package com.ybugmobile.vaktiva.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ybugmobile.vaktiva.R

@Composable
fun AudioSettingsScreen(
    viewModel: AudioSettingsViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null
) {
    val audioItems by viewModel.audioItems.collectAsState()
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.addCustomAudio(it) }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { launcher.launch("audio/*") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Custom") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Text(
                        text = "Select Adhan Sound",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "This sound will play during prayer times.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Supported formats: MP3, AAC, WAV, OGG",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                items(audioItems) { item ->
                    AudioFileItem(
                        item = item,
                        onSelect = { viewModel.selectAudio(item.path) },
                        onTogglePreview = { viewModel.togglePreview(item.path) },
                        onDelete = if (!item.isDefault) { { viewModel.deleteAudio(item.path) } } else null
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
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
    val borderColor = if (item.isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (item.isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection Indicator
            Icon(
                imageVector = if (item.isSelected) Icons.Default.CheckCircle else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (item.isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Normal
                )
                if (item.isDefault) {
                    Text(
                        text = "Built-in",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Preview Button
            IconButton(onClick = onTogglePreview) {
                Icon(
                    imageVector = if (item.isPlaying) Icons.Default.CheckCircle else Icons.Default.PlayArrow, // Replace with Stop icon if available
                    contentDescription = "Preview",
                    tint = if (item.isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            
            // Delete Button
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
