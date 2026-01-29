package com.ybugmobile.vaktiva

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.scale

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(this@MainActivity, viewModel)
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 24.dp, bottom = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            // Time Shift Test Button
                            SmallFloatingActionButton(
                                onClick = { 
                                    viewModel.debugAddHours(1)
                                    Toast.makeText(this@MainActivity, "Time shifted +1 hour", Toast.LENGTH_SHORT).show()
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(Icons.Default.Update, contentDescription = "Shift Time")
                            }

                            // Alarm Test Button
                            FloatingActionButton(
                                onClick = { 
                                    val seconds = 70
                                    viewModel.triggerTestAlarm(seconds)
                                    Toast.makeText(this@MainActivity, "Test alarm scheduled for $seconds seconds", Toast.LENGTH_SHORT).show()
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = "Test Alarm")
                            }
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

    val items = listOf(
        NavigationItem(Screen.Home.route, R.string.nav_home, Icons.Rounded.Home),
        NavigationItem(Screen.Qibla.route, R.string.nav_qibla, Icons.Rounded.LocationOn),
        NavigationItem(Screen.Settings.route, R.string.nav_settings, Icons.Rounded.Settings)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = items.any { it.route == currentDestination?.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                SmoothTouchNavigationBar(
                    items = items,
                    currentRoute = currentDestination?.route,
                    onItemClick = { route ->
                        navController.navigate(route) {
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
    ) { innerPadding ->
        NavHost(
            navController, 
            startDestination = startDestination, 
            Modifier.padding(innerPadding)
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

data class NavigationItem(val route: String, val labelResId: Int, val icon: ImageVector)

@Composable
fun SmoothTouchNavigationBar(
    items: List<NavigationItem>,
    currentRoute: String?,
    onItemClick: (String) -> Unit
) {
    val density = LocalDensity.current
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }
    var tabWidth by remember { mutableStateOf(0.dp) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .onGloballyPositioned {
                    tabWidth = with(density) { (it.size.width / items.size).toDp() }
                }
        ) {
            // Smooth Sliding Indicator
            if (selectedIndex != -1 && tabWidth > 0.dp) {
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * selectedIndex,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "indicatorOffset"
                )

                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onItemClick(item.route) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(300),
                            label = "contentColor"
                        )
                        
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "iconScale"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(26.dp)
                                    .scale(iconScale)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = item.labelResId),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}
