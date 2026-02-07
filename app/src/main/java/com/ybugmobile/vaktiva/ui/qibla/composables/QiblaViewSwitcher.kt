package com.ybugmobile.vaktiva.ui.qibla.composables

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun QiblaViewSwitcher(
    isMapView: Boolean,
    onViewChange: (Boolean) -> Unit,
    contentColor: Color,
    containerColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.height(44.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwitcherButton(
                isSelected = !isMapView,
                icon = Icons.Default.Explore,
                contentColor = contentColor,
                isMapView = isMapView,
                onClick = { onViewChange(false) }
            )
            SwitcherButton(
                isSelected = isMapView,
                icon = Icons.Default.Map,
                contentColor = contentColor,
                isMapView = isMapView,
                onClick = { onViewChange(true) }
            )
        }
    }
}

@Composable
private fun SwitcherButton(
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentColor: Color,
    isMapView: Boolean,
    onClick: () -> Unit
) {
    val theme = MaterialTheme.colorScheme
    
    val bgColor by animateColorAsState(
        if (isSelected) {
            if (isMapView) theme.primary else Color.White.copy(alpha = 0.9f)
        } else Color.Transparent,
        label = "bg"
    )
    val iconColor by animateColorAsState(
        if (isSelected) {
            if (isMapView) theme.onPrimary else Color.Black
        } else {
            if (isMapView) theme.onSurface.copy(alpha = 0.6f) else contentColor.copy(alpha = 0.7f)
        },
        label = "icon"
    )

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(44.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
