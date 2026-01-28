package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ybugmobile.vaktiva.R

@Composable
fun HomeHeader(
    locationName: String,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp), // Reduced from 56.dp to move it higher
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = locationName.substringBefore(",")
                    .ifEmpty { stringResource(R.string.home_unknown_location) },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                color = contentColor
            )
        }
        Text(
            text = locationName.substringAfter(", ").ifEmpty { "" },
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 26.dp) // Aligns with the text after icon
        )
    }
}
