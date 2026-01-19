package com.ybugmobile.vaktiva.ui.welcome

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.ybugmobile.vaktiva.ui.home.HomeViewModel

private val WelcomeGradientStart = Color(0xFF1A237E) // Deep Blue
private val WelcomeGradientEnd = Color(0xFF3949AB)   // Lighter Blue
private val ActionButtonColor = Color(0xFFFFD700)    // Gold accent

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WelcomeScreen(
    onSetupComplete: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showRationaleDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var permissionResultReceived by remember { mutableStateOf(false) }

    // Define permissions based on Android version
    val permissions = remember {
        mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val permissionState = rememberMultiplePermissionsState(permissions) { result ->
        val locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                              result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (locationGranted) {
            viewModel.onPermissionsGranted()
            onSetupComplete()
        } else {
            permissionResultReceived = true
        }
    }

    LaunchedEffect(permissionResultReceived) {
        if (permissionResultReceived) {
            if (permissionState.shouldShowRationale) {
                showRationaleDialog = true
            } else {
                showSettingsDialog = true
            }
            permissionResultReceived = false
        }
    }

    // Modern Gradient Background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            WelcomeGradientStart,
            WelcomeGradientEnd
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(24.dp)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Welcome Text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = "Welcome to Vaktiva",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Your modern companion for prayer times and spiritual guidance.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }

            // Middle Section: Features/Permissions
            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(vertical = 32.dp)
            ) {
                FeatureItem(
                    icon = Icons.Default.LocationOn,
                    title = "Location Access",
                    description = "Required to calculate accurate prayer times and Qibla direction for your specific location."
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    FeatureItem(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        description = "Get timely reminders for Adhan and prayer alerts so you never miss a prayer."
                    )
                }
            }

            // Bottom Section: Action Button
            Button(
                onClick = {
                    if (permissionState.allPermissionsGranted) {
                        viewModel.onPermissionsGranted()
                        onSetupComplete()
                    } else {
                        permissionState.launchMultiplePermissionRequest()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ActionButtonColor,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                val buttonText = if (permissionState.allPermissionsGranted) "Continue" else "Get Started"
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Rationale Dialog
    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            title = { Text("Permissions Needed") },
            text = { 
                Text("Vaktiva needs location access to provide accurate prayer times for your area. Please grant the permissions.") 
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    permissionState.launchMultiplePermissionRequest()
                }) {
                    Text("Try Again")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationaleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Settings Dialog (Permanently Denied)
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Permissions Permanently Denied") },
            text = { 
                Text("You have denied location permissions. To use Vaktiva, please enable them in the app settings.") 
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun FeatureItem(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
