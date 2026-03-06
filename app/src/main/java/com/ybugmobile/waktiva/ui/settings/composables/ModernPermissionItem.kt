package com.ybugmobile.waktiva.ui.settings.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme

@Composable
fun ModernPermissionItem(
    title: String,
    subtitle: String? = null,
    isGranted: Boolean,
    icon: ImageVector
) {
    val glassTheme = LocalGlassTheme.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else Color(0xFFF87171).copy(alpha = 0.15f), 
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFF87171),
                modifier = Modifier.size(18.dp)
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
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = glassTheme.secondaryContentColor,
                        lineHeight = 16.sp
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Surface(
            shape = CircleShape,
            color = if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.2f) 
                    else Color(0xFFF87171).copy(alpha = 0.2f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, 
                if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.3f) 
                else Color(0xFFF87171).copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = (if (isGranted) stringResource(R.string.settings_granted) 
                        else stringResource(R.string.settings_denied)).uppercase(),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = glassTheme.contentColor
            )
        }
    }
}
