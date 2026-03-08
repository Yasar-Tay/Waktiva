package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ybugmobile.waktiva.domain.model.WeatherCondition

@Composable
fun DebugWeatherDialog(
    onDismiss: () -> Unit,
    onConditionSelected: (WeatherCondition) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test Weather Conditions") },
        text = {
            LazyColumn {
                items(WeatherCondition.entries.filter { it != WeatherCondition.UNKNOWN }) { condition ->
                    TextButton(
                        onClick = { onConditionSelected(condition) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(condition.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
