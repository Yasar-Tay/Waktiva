package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ybugmobile.waktiva.R
import com.ybugmobile.waktiva.data.local.preferences.UserSettings
import com.ybugmobile.waktiva.ui.theme.GlassTheme

@Composable
fun CalculationMethodCard(
    settings: UserSettings?,
    calculationMethods: List<Pair<Int, Int>>,
    onClick: () -> Unit,
    contentColor: Color,
    glassTheme: GlassTheme
) {
    settings?.let { s ->
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = glassTheme.secondaryContentColor.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        stringResource(R.string.settings_method).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = glassTheme.secondaryContentColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        calculationMethods.find { it.second == s.calculationMethod }?.first?.let { stringResource(it) }
                            ?: "Default",
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                }
                Icon(
                    Icons.Default.Settings,
                    null,
                    tint = glassTheme.secondaryContentColor
                )
            }
        }
    }
}
