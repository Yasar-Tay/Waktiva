package com.ybugmobile.vaktiva

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.work.*
import com.ybugmobile.vaktiva.data.worker.LocationUpdateWorker
import com.ybugmobile.vaktiva.data.worker.PrayerUpdateWorker
import com.ybugmobile.vaktiva.ui.home.HomeScreen
import com.ybugmobile.vaktiva.ui.home.HomeViewModel
import com.ybugmobile.vaktiva.ui.navigation.Screen
import com.ybugmobile.vaktiva.ui.qibla.QiblaScreen
import com.ybugmobile.vaktiva.ui.settings.SettingsScreen
import com.ybugmobile.vaktiva.ui.theme.VaktivaTheme
import com.ybugmobile.vaktiva.ui.welcome.WelcomeScreen
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Recommendation: Move scheduleWork() to your Application class onCreate()
        // to ensure it runs once per app process, not every activity recreation.
        if (savedInstanceState == null) {
            scheduleWork()
        }
        
        enableEdgeToEdge()
        setContent {
            VaktivaTheme {
                MainNavigation(this)
            }
        }
    }

    private fun scheduleWork() {
        val workManager = WorkManager.getInstance(this)
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val prayerRequest = PeriodicWorkRequestBuilder<PrayerUpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "PrayerUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            prayerRequest
        )

        val locationRequest = PeriodicWorkRequestBuilder<LocationUpdateWorker>(4, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "LocationUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            locationRequest
        )
    }
}

@Composable
fun MainNavigation(context: Context, viewModel: HomeViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val settings by viewModel.settings.collectAsState(initial = null)
    
    // Determine start destination based on permissions and settings
    val startDestination = remember(settings) {
        if (settings == null) null
        else {
            val hasLocationPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasLocationPermission) Screen.Home.route
            else Screen.Welcome.route
        }
    }

    if (startDestination == null) return

    val items = listOf(Screen.Home, Screen.Qibla, Screen.Settings)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = items.any { it.route == currentDestination?.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon!!, contentDescription = null) },
                            label = { Text(screen.label!!) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController, 
            startDestination = startDestination, 
            Modifier.padding(if (showBottomBar) innerPadding else androidx.compose.foundation.layout.PaddingValues(0.dp))
        ) {
            composable(Screen.Welcome.route) { 
                WelcomeScreen(
                    onSetupComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Qibla.route) { QiblaScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
