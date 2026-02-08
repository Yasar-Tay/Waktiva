package com.ybugmobile.vaktiva.ui.settings.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHealthOverlay(
    onDismiss: () -> Unit
) {
    // Modern Sleek Palette
    val backgroundColor = Color(0xFFF8FAFC)
    val accentColor = Color(0xFF6366F1) // Indigo 500
    val textColor = Color(0xFF0F172A)
    val secondaryText = Color(0xFF64748B)

    var hasIssues by remember { mutableStateOf(true) }

    LaunchedEffect(hasIssues) {
        if (!hasIssues) {
            onDismiss()
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = backgroundColor,
        dragHandle = { 
            BottomSheetDefaults.DragHandle(color = Color(0xFFE2E8F0)) 
        },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Section with Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(accentColor, accentColor.copy(alpha = 0.7f))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HealthAndSafety,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = stringResource(R.string.health_title),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp,
                            lineHeight = 28.sp
                        ),
                        color = textColor
                    )
                    Text(
                        text = stringResource(R.string.health_overlay_subtitle),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.sp
                        ),
                        color = secondaryText
                    )
                }
            }

            // Section Title for Issues
            Text(
                text = "Detected Issues".uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                ),
                color = secondaryText.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )

            // SystemHealthCard content (List of issues)
            SystemHealthCard(
                showBackground = false,
                showTitle = false,
                contentColor = textColor,
                onIssuesChanged = { hasIssues = it }
            )

        }
    }
}
