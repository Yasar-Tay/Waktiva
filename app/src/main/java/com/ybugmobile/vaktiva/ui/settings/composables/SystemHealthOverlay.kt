package com.ybugmobile.vaktiva.ui.settings.composables

import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.vaktiva.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHealthOverlay(
    isNetworkAvailable: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // Using solid dark theme for guaranteed readability
    val solidBg = Color(0xFF111827) // Deep Slate
    val pureWhite = Color.White
    val secondaryWhite = Color.White.copy(alpha = 0.7f)
    val warningAccent = Color(0xFFFACC15)
    
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
