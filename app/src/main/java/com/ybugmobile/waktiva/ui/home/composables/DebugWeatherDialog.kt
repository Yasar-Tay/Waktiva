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

/**
 * A developer-only dialog used to manually override and test different weather conditions.
 * Allows visual verification of particle effects and background gradients without waiting 
 * for real weather changes.
 *
 * @param onDismiss Callback to close the dialog.
 * @param onConditionSelected Callback when a specific weather state is picked for testing.
 */
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
