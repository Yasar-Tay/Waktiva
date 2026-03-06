package com.ybugmobile.waktiva

import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.work.*
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.worker.LocationUpdateWorker
import com.ybugmobile.waktiva.data.worker.PrayerUpdateWorker
import com.ybugmobile.waktiva.domain.manager.TimeManager
import com.ybugmobile.waktiva.ui.home.HomeScreen
import com.ybugmobile.waktiva.ui.home.HomeViewModel
import com.ybugmobile.waktiva.ui.donation.DonateScreen
import com.ybugmobile.waktiva.ui.navigation.Screen
import com.ybugmobile.waktiva.ui.qibla.QiblaScreen
import com.ybugmobile.waktiva.ui.settings.AudioSettingsScreen
import com.ybugmobile.waktiva.ui.settings.LicensesScreen
import com.ybugmobile.waktiva.ui.settings.SettingsScreen
import com.ybugmobile.waktiva.ui.theme.WaktivaBackgroundWrapper
import com.ybugmobile.waktiva.ui.theme.WaktivaTheme
import com.ybugmobile.waktiva.ui.welcome.WelcomeScreen
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The main activity of the application, serving as the primary entry point for the UI.
 * 
 * Responsibilities:
 * - Setting up Jetpack Compose with a custom Material 3 theme ([WaktivaTheme]).
 * - Configuring edge-to-edge display for immersive UI.
 * - Initializing [WorkManager] for background data syncing (Prayer & Location updates).
 * - Managing top-level navigation and screen routing.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var timeManager: TimeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Schedule periodic background tasks if this is the first time the activity is created
        if (savedInstanceState == null) {
            scheduleWork()
        }
        
        enableEdgeToEdge()
        setContent {
            WaktivaTheme {
                val homeViewModel: HomeViewModel = hiltViewModel()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(this@MainActivity, homeViewModel, timeManager)
                }
            }
        }
    }

    /**
     * Enqueues unique periodic work for both prayer time updates and location tracking.
     * These tasks ensure data remains accurate even when the app is not in the foreground.
     */
    private fun scheduleWork() {
        val workManager = WorkManager.getInstance(this)
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Sync prayer times every 24 hours
        val prayerRequest = PeriodicWorkRequestBuilder<PrayerUpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "PrayerUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            prayerRequest
        )

        // Sync location every 4 hours to adjust prayer times if the user has moved
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

/**
 * Root-level composable that manages the navigation graph and the global UI layout
 * (including background wrappers and bottom/rail navigation bars).
 */
@Composable
fun MainNavigation(context: Context, homeViewModel: HomeViewModel, timeManager: TimeManager) {
    val navController = rememberNavController()
    val settings by homeViewModel.settings.collectAsState(initial = null)
    val homeState by homeViewModel.state.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // Determine where to send the user based on onboarding status
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
        NavigationItem(Screen.Donate.route, R.string.nav_donate, Icons.Default.Favorite),
        NavigationItem(Screen.Settings.route, R.string.nav_settings, Icons.Rounded.Settings)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showNavigationLayout = items.any { it.route == currentDestination?.route }

    // State to control navigation bar visibility based on scroll gestures
    var isNavVisible by remember { mutableStateOf(true) }
    
    // Force visibility in landscape as the rail is fixed
    LaunchedEffect(isLandscape) {
        if (isLandscape) isNavVisible = true
    }
    
    // Intercept scroll events to hide/show the bottom navigation bar
    val nestedScrollConnection = remember(isLandscape) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Toggle visibility based on swipe direction in portrait mode
                if (!isLandscape) {
                    if (available.y > 15) {
                        isNavVisible = true 
                    } else if (available.y < -15) {
                        isNavVisible = false 
                    }
                }
                return Offset.Zero
            }
        }
    }

    WaktivaBackgroundWrapper(
        currentTime = homeState.currentTime.toLocalTime(),
        prayerDay = homeState.currentPrayerDay
    ) {
        Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
            NavHost(
                navController, 
                startDestination = startDestination, 
                modifier = Modifier.fillMaxSize()
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
                composable(Screen.Home.route) { 
                    HomeScreen(viewModel = homeViewModel) 
                }
                composable(Screen.Qibla.route) { QiblaScreen() }
                composable(Screen.Donate.route) { 
                    DonateScreen(onBack = { navController.popBackStack() }) 
                }
                composable(Screen.Settings.route) { 
                    SettingsScreen(
                        onNavigateToAudio = {
                            navController.navigate(Screen.AudioSettings.route)
                        },
                        onNavigateToLicenses = {
                            navController.navigate(Screen.Licenses.route)
                        }
                    ) 
                }
                composable(Screen.AudioSettings.route) {
                    AudioSettingsScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Licenses.route) {
                    LicensesScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // Landscape Navigation Rail (Fixed at start)
            if (showNavigationLayout && isLandscape) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .displayCutoutPadding() 
                        .systemBarsPadding()   
                ) {
                    SmoothTouchNavigationRail(
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

            // Portrait Bottom Navigation Bar (Animated)
            if (showNavigationLayout && !isLandscape) {
                AnimatedVisibility(
                    visible = isNavVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                ) {
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

            // Global floating actions (Debug/Development only in current state)
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val density = LocalDensity.current
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = if (showNavigationLayout && !isLandscape) 100.dp else 0.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    SmallFloatingActionButton(
                        onClick = { 
                            timeManager.addMinutes(30)
                            //Toast.makeText(context, "Time shifted +30 minutes", Toast.LENGTH_SHORT).show()
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Update, contentDescription = "Shift Time")
                    }

                    FloatingActionButton(
                        onClick = { 
                            val seconds = 5
                            homeViewModel.triggerTestAlarm(seconds)
                            Toast.makeText(context, "Test alarm scheduled for $seconds seconds", Toast.LENGTH_SHORT).show()
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

/** Data class representing a tab in the navigation layout. */
data class NavigationItem(val route: String, val labelResId: Int, val icon: ImageVector)

/** A custom navigation rail for tablets or landscape phones with smooth selection indicators. */
@Composable
fun SmoothTouchNavigationRail(
    items: List<NavigationItem>,
    currentRoute: String?,
    onItemClick: (String) -> Unit
) {
    val density = LocalDensity.current
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }
    var tabHeight by remember { mutableStateOf(0.dp) }

    Surface(
        color = Color(0xFFF8F9FA), // Solid light color
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        modifier = Modifier
            .fillMaxHeight()
            .width(80.dp)
            .padding(vertical = 40.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    tabHeight = with(density) { (it.size.height / items.size).toDp() }
                }
        ) {
            if (selectedIndex != -1 && tabHeight > 0.dp) {
                val indicatorOffset by animateDpAsState(
                    targetValue = tabHeight * selectedIndex,
                    animationSpec = spring(0.8f, Spring.StiffnessMediumLow),
                    label = "indicatorOffset"
                )

                Box(
                    modifier = Modifier
                        .offset(y = indicatorOffset)
                        .height(tabHeight)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = Color(0xFFE9ECEF), // Light gray indicator
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onItemClick(item.route) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) Color(0xFF212529) else Color(0xFF6C757D),
                            label = "contentColor"
                        )
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

/** A modern, animated bottom navigation bar with a solid light background and spring selection. */
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
        color = Color(0xFFF8F9FA), // Solid light color
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    tabWidth = with(density) { (it.size.width / items.size).toDp() }
                }
        ) {
            if (selectedIndex != -1 && tabWidth > 0.dp) {
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * selectedIndex,
                    animationSpec = spring(0.8f, Spring.StiffnessMediumLow),
                    label = "indicatorOffset"
                )

                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = Color(0xFFE9ECEF), // Light gray indicator
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
                            targetValue = if (isSelected) Color(0xFF212529) else Color(0xFF6C757D),
                            label = "contentColor"
                        )
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
