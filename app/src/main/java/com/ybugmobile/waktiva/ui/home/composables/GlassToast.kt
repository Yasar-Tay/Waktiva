package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.ui.theme.IBMPlexArabic
import com.ybugmobile.waktiva.ui.theme.LocalGlassTheme
import kotlinx.coroutines.delay

@Composable
fun GlassToast(
    message: String,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(isVisible, message) {
        if (isVisible) {
            delay(2000)
            onDismiss()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            val glassTheme = LocalGlassTheme.current
            
            Surface(
                color = glassTheme.containerColor.copy(alpha = 0.35f),
                contentColor = glassTheme.contentColor,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, glassTheme.borderColor.copy(alpha = 0.3f)),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .widthIn(max = 300.dp)
            ) {
                Text(
                    text = message,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = IBMPlexArabic,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.2.sp
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
        }
    }
}
