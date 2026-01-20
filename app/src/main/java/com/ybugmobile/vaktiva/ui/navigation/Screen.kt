package com.ybugmobile.vaktiva.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String? = null, val icon: ImageVector? = null) {
    object Welcome : Screen("welcome")
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Qibla : Screen("qibla", "Qibla", Icons.Default.LocationOn)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object AudioSettings : Screen("audio_settings", "Audio Settings")
}