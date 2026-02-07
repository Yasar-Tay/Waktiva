package com.ybugmobile.vaktiva.ui.settings.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHealthOverlay(
    onDismiss: () -> Unit
) {
    
    // Using solid dark theme for guaranteed readability
    val solidBg = Color(0xFF111827) // Deep Slate
    val pureWhite = Color.White
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = solidBg,
        contentColor = pureWhite,
        dragHandle = { BottomSheetDefaults.DragHandle(color = pureWhite.copy(alpha = 0.2f)) },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // The main card will now detect its container and adjust
            SystemHealthCard()
        }
    }
}
