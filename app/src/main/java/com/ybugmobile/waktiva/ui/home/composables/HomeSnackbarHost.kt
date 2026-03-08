package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.ui.theme.GlassTheme

@Composable
fun HomeSnackbarHost(
    hostState: SnackbarHostState,
    glassTheme: GlassTheme,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data ->
            val parts = data.visuals.message.split(":", limit = 2)
            val type = parts.getOrNull(0)
            val message = parts.getOrNull(1) ?: data.visuals.message

            val icon = when (type) {
                "MUTED" -> Icons.AutoMirrored.Filled.VolumeOff
                "UNMUTED" -> Icons.AutoMirrored.Filled.VolumeUp
                else -> null
            }

            val snackbarBackgroundColor = if (glassTheme.isLightMode) {
                Color.White.copy(alpha = 0.9f)
            } else {
                Color(0xFF1C1B1F).copy(alpha = 0.8f)
            }

            val snackbarContentColor = if (glassTheme.isLightMode) {
                Color(0xFF1C1B1F)
            } else {
                Color.White
            }

            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .wrapContentWidth(),
                shape = RoundedCornerShape(percent = 50),
                color = snackbarBackgroundColor,
                contentColor = snackbarContentColor,
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    snackbarContentColor.copy(alpha = 0.12f)
                ),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = snackbarContentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp
                        ),
                        color = snackbarContentColor
                    )
                }
            }
        }
    )
}
