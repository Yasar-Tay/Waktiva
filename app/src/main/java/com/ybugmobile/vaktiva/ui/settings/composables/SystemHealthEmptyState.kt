package com.ybugmobile.vaktiva.ui.settings.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ybugmobile.vaktiva.R
import com.ybugmobile.vaktiva.ui.theme.GlassTheme

@Composable
fun SystemHealthEmptyState(
    isRefreshing: Boolean,
    hasPrayerData: Boolean,
    contentColor: Color,
    glassTheme: GlassTheme,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = glassTheme.containerColor,
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.padding(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, glassTheme.borderColor)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = contentColor
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.qibla_locating),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.ReportProblem,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.health_issues_detected),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.health_overlay_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    
                    SystemHealthCard(
                        hasPrayerData = hasPrayerData,
                        showBackground = false,
                        showTitle = false,
                        contentColor = contentColor
                    )
                }
            }
        }
    }
}
