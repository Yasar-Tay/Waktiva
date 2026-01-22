package com.ybugmobile.vaktiva

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.ybugmobile.vaktiva.receiver.PrayerAlarmReceiver
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
                Box(modifier = Modifier.fillMaxSize()) {
                    MainNavigation(this@MainActivity)
                    
                    // Test FAB to schedule alarm in 10 seconds
                    FloatingActionButton(
                        onClick = { scheduleTestAlarm() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 80.dp),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = "Test Alarm")
                    }
                }
            }
        }
    }

    private fun scheduleTestAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, PrayerAlarmReceiver::class.java).apply {
            putExtra("PRAYER_NAME", "Fajr")
            action = "com.ybugmobile.vaktiva.ACTION_PRAYER_ALARM"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            2002, // Unique ID for test
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = System.currentTimeMillis() + 10000 // 10 seconds

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, pendingIntent),
            pendingIntent
        )
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
