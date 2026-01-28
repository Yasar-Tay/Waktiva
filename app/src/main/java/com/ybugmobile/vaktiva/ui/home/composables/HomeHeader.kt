package com.ybugmobile.vaktiva.ui.home.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = locationName.substringBefore(",")
                .ifEmpty { stringResource(R.string.home_unknown_location) },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Light,
            color = contentColor
        )
        Text(
            text = locationName.substringAfter(", ").ifEmpty { "" },
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor.copy(alpha = 0.7f)
        )
    }
}
