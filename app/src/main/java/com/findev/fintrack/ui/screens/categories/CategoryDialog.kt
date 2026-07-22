package com.findev.fintrack.ui.screens.categories

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.data.local.entity.CategoryType
import com.findev.fintrack.ui.FieldShape
import com.findev.fintrack.ui.GlassAlertDialog
import com.findev.fintrack.ui.PillSelector
import com.findev.fintrack.ui.fieldColors
import com.findev.fintrack.ui.formatAmountForInput
import com.findev.fintrack.ui.parseAmountToMinor
import com.findev.fintrack.ui.sanitizeAmountInput

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
    onConfirm: (name: String, type: CategoryType, icon: String, color: Long, monthlyLimitMinor: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var icon by remember { mutableStateOf(category?.icon ?: "🙂") }
    var color by remember { mutableLongStateOf(category?.color ?: PALETTE.first()) }
    var type by remember { mutableStateOf(category?.type ?: initialType) }
    var limit by remember {
        mutableStateOf(category?.monthlyLimitMinor?.let { formatAmountForInput(it) }.orEmpty())
    }

    val trimmedName = name.trim()
    val trimmedIcon = icon.trim()

    GlassAlertDialog(
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
                    PillSelector(
                        options = options.map { (option, labelRes) -> option to stringResource(labelRes) },
                        selected = type,
                        onSelected = { type = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = FieldShape,
                    colors = fieldColors(),
                    label = { Text(stringResource(R.string.categories_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = icon,
                    // An emoji can be several code points, so allow a few characters.
                    onValueChange = { icon = it.take(4) },
                    singleLine = true,
                    shape = FieldShape,
                    colors = fieldColors(),
                    label = { Text(stringResource(R.string.categories_icon)) },
                    // Live preview: the swatch shows what the row will actually look like.
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(32.dp)
                                .background(Color(color.toInt()).copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = icon, style = MaterialTheme.typography.titleMedium)
                        }
                    },
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
                        .horizontalScroll(rememberScrollState())
                        // The selected swatch scales up past its box (draw-only), and the
                        // scroll viewport clips that overflow - so the first and last swatches
                        // lost an edge when picked. Inner padding gives the growth room to spill
                        // into instead of being cut off.
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PALETTE.forEach { swatch ->
                        val selected = swatch == color
                        // The chosen swatch grows instead of just gaining a ring, so the
                        // selection is obvious at a glance on a crowded row.
                        val scale by animateFloatAsState(
                            targetValue = if (selected) 1.18f else 1f,
                            label = "swatchScale",
                        )
                        val ring by animateDpAsState(
                            targetValue = if (selected) 3.dp else 0.dp,
                            label = "swatchRing",
                        )
                        Column(
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .size(32.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .background(Color(swatch.toInt()), CircleShape)
                                .border(ring, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = swatch },
                        ) {}
                    }
                }

                // A budget only applies to spending; the field is hidden for income categories.
                if (type == CategoryType.EXPENSE) {
                    TextField(
                        value = limit,
                        onValueChange = { limit = sanitizeAmountInput(it) },
                        singleLine = true,
                        shape = FieldShape,
                        colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text(stringResource(R.string.categories_monthly_limit)) },
                        supportingText = { Text(stringResource(R.string.categories_monthly_limit_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = trimmedName.isNotEmpty() && trimmedIcon.isNotEmpty(),
                onClick = {
                    // A zero or blank limit means "no budget"; only expense categories carry one.
                    val limitMinor = parseAmountToMinor(limit).takeIf { it > 0 }
                    onConfirm(
                        trimmedName,
                        type,
                        trimmedIcon,
                        color,
                        if (type == CategoryType.EXPENSE) limitMinor else null,
                    )
                },
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
