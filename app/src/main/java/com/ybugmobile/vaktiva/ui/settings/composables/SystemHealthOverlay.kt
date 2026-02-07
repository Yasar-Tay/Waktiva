package com.ybugmobile.vaktiva.ui.settings.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHealthOverlay(
    onDismiss: () -> Unit
) {
    // Modern Light Design
    val backgroundColor = Color(0xFFF8FAFC) // Ultra light slate
    val textColor = Color(0xFF0F172A) // Slate 900
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = backgroundColor,
        contentColor = textColor,
        dragHandle = { 
            BottomSheetDefaults.DragHandle(
                color = textColor.copy(alpha = 0.1f)
            ) 
        },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            // SystemHealthCard without its own background and border
            SystemHealthCard(
                showBackground = false,
                contentColor = textColor
            )
        }
    }
}
