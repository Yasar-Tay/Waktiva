package com.ybugmobile.vaktiva.ui.settings.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.ui.theme.GlassTheme

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    glassTheme: GlassTheme,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(glassTheme.contentColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = glassTheme.contentColor.copy(alpha = 0.8f), 
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, 
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = glassTheme.contentColor
                )
            )
            Text(
                text = subtitle, 
                style = MaterialTheme.typography.bodySmall.copy(
                    color = glassTheme.secondaryContentColor,
                    letterSpacing = 0.5.sp
                )
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = glassTheme.contentColor,
                checkedTrackColor = Color(0xFF81C784).copy(alpha = 0.5f),
                uncheckedThumbColor = glassTheme.contentColor.copy(alpha = 0.4f),
                uncheckedTrackColor = glassTheme.contentColor.copy(alpha = 0.1f),
                uncheckedBorderColor = glassTheme.contentColor.copy(alpha = 0.2f)
            )
        )
    }
}
