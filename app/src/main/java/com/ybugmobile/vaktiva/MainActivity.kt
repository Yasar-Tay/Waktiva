package com.ybugmobile.vaktiva

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.ybugmobile.vaktiva.ui.settings.AudioSettingsScreen
import com.ybugmobile.vaktiva.ui.settings.SettingsScreen
import com.ybugmobile.vaktiva.ui.theme.VaktivaTheme
import com.ybugmobile.vaktiva.ui.welcome.WelcomeScreen
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (savedInstanceState == null) {
            scheduleWork()
        }
        
        enableEdgeToEdge()
        setContent {
            VaktivaTheme {
                val viewModel: HomeViewModel = hiltViewModel()
                
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainNavigation(this@MainActivity, viewModel)
                    
                    // Refactored Test FAB to use centralized logic
                    Box(modifier = Modifier.fillMaxSize()) {
                        FloatingActionButton(
                            onClick = { 
                                val seconds = 70
                                viewModel.triggerTestAlarm(seconds)
                                Toast.makeText(this@MainActivity, "Test alarm scheduled for $seconds seconds", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 100.dp), // Adjusted bottom padding
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = "Test Alarm")
                        }
                    }
                }
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
fun MainNavigation(context: Context, viewModel: HomeViewModel) {
    val navController = rememberNavController()
    val settings by viewModel.settings.collectAsState(initial = null)
    
    val startDestination = remember(settings) {
        if (settings == null) null
        else {
            if (settings?.isSetupComplete == true) Screen.Home.route
            else Screen.Welcome.route
        }
    }

    if (startDestination == null) return

    val items = listOf(Screen.Home, Screen.Qibla, Screen.Settings)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = items.any { it.route == currentDestination?.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
            Modifier.padding(innerPadding) // Consuming the padding correctly
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
            composable(Screen.Home.route) { HomeScreen(viewModel = viewModel) }
            composable(Screen.Qibla.route) { QiblaScreen() }
            composable(Screen.Settings.route) { 
                SettingsScreen(
                    onNavigateToAudio = {
                        navController.navigate(Screen.AudioSettings.route)
                    }
                ) 
            }
            composable(Screen.AudioSettings.route) {
                AudioSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
