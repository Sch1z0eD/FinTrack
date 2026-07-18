package com.findev.fintrack.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.data.local.entity.CategoryType

/** Same palette the seeded categories use, so custom ones do not look foreign. */
private val PALETTE = listOf(
    0xFF4CAF50, 0xFF8BC34A, 0xFF009688, 0xFF00BCD4, 0xFF2196F3,
    0xFF3F51B5, 0xFF9C27B0, 0xFFE91E63, 0xFFF44336, 0xFFFF5722,
    0xFFFF9800, 0xFFFFC107, 0xFF795548, 0xFF607D8B, 0xFF9E9E9E,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDialog(
    category: CategoryEntity?,
    /** Preselected type for a new category; ignored when editing. */
    initialType: CategoryType,
    onConfirm: (name: String, type: CategoryType, icon: String, color: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var icon by remember { mutableStateOf(category?.icon ?: "🙂") }
    var color by remember { mutableLongStateOf(category?.color ?: PALETTE.first()) }
    var type by remember { mutableStateOf(category?.type ?: initialType) }

    val trimmedName = name.trim()
    val trimmedIcon = icon.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (category == null) R.string.categories_new else R.string.categories_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (category == null) {
                    // Changing the type of an existing category would move it between grids.
                    val options = listOf(
                        CategoryType.EXPENSE to R.string.quick_entry_expense,
                        CategoryType.INCOME to R.string.quick_entry_income,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        options.forEachIndexed { index, (option, labelRes) ->
                            SegmentedButton(
                                selected = type == option,
                                onClick = { type = option },
                                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                            ) {
                                Text(stringResource(labelRes))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.categories_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = icon,
                    // An emoji can be several code points, so allow a few characters.
                    onValueChange = { icon = it.take(4) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.categories_icon)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.categories_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PALETTE.forEach { swatch ->
                        val selected = swatch == color
                        Column(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .size(32.dp)
                                .background(Color(swatch.toInt()), CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            3.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { color = swatch },
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = trimmedName.isNotEmpty() && trimmedIcon.isNotEmpty(),
                onClick = { onConfirm(trimmedName, type, trimmedIcon, color) },
            ) {
                Text(
                    stringResource(
                        if (category == null) R.string.account_create_confirm else R.string.account_save,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.account_create_cancel))
            }
        },
    )
}
