package com.elejar.ZentraDL.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elejar.ZentraDL.R
import com.elejar.ZentraDL.data.local.Category

/** "Move to …" picker shared by the list and details (P3c). */
@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    currentId: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to)) },
        text = {
            Column {
                categories.forEach { c ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onPick(c.id); onDismiss() }
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = c.id == currentId, onClick = null)
                        Text(c.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
