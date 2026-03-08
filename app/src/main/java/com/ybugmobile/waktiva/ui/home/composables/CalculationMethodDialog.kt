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
                                onClick = null
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
