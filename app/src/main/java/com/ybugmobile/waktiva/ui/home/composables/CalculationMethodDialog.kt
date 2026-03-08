package com.ybugmobile.waktiva.ui.home.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ybugmobile.waktiva.R

/**
 * A dialog that allows users to choose from various prayer calculation methods.
 * Displays a list of available methods with radio buttons for selection.
 *
 * @param showDialog Whether the dialog is currently visible.
 * @param onDismiss Callback invoked when the dialog should be closed without selection or via the OK button.
 * @param calculationMethods List of pairs where the first element is the string resource ID for the method name
 *                           and the second element is the method's unique ID.
 * @param selectedMethod The ID of the currently active calculation method.
 * @param onMethodSelected Callback invoked when a new method is chosen from the list.
 */
@Composable
fun CalculationMethodDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    calculationMethods: List<Pair<Int, Int>>,
    selectedMethod: Int,
    onMethodSelected: (Int) -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_method)) },
            text = {
                LazyColumn {
                    items(calculationMethods) { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMethodSelected(method.second)
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMethod == method.second,
                                onClick = null // Handled by row clickable
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(stringResource(method.first))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}
