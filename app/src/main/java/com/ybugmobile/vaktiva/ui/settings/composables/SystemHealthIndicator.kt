package com.ybugmobile.vaktiva.ui.settings.composables

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ybugmobile.vaktiva.utils.PermissionUtils

@Composable
fun SystemHealthIndicator(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasIssues by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(false) }

    fun checkHealth() {
        val locPerm = PermissionUtils.isLocationPermissionGranted(context)
        val notifPerm = PermissionUtils.isNotificationPermissionGranted(context)
        val gpsEnabled = PermissionUtils.isLocationEnabled(context)
        val channelsMuted = PermissionUtils.areNotificationChannelsMuted(context)
        val dndActive = PermissionUtils.isDoNotDisturbActive(context)
        isOffline = !PermissionUtils.isNetworkAvailable(context)

        hasIssues = !locPerm || !notifPerm || !gpsEnabled || channelsMuted || dndActive || isOffline
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkHealth()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Initial check
    LaunchedEffect(Unit) {
        checkHealth()
    }

    if (hasIssues) {
        val color = Color(0xFFFF5252)
        val iconPulseTransition = rememberInfiniteTransition(label = "iconPulse")
        val iconScale by iconPulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "iconScale"
        )

        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 10.dp,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(2.dp, color.copy(alpha = 0.7f)),
            modifier = modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isOffline) Icons.Rounded.WifiOff else Icons.Rounded.PriorityHigh,
                    contentDescription = "Status",
                    tint = color,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                )
            }
        }
    }
}
