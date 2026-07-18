package com.findev.fintrack.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.findev.fintrack.R
import com.findev.fintrack.data.local.TransactionListItem
import com.findev.fintrack.data.local.entity.TransactionType
import com.findev.fintrack.ui.dateLabel
import com.findev.fintrack.ui.formatMinor

/**
 * Read-only look at a transaction. Editing lives behind the swipe actions on purpose,
 * so a stray tap in the feed can never start changing data.
 *
 * Everything shown here already comes with the feed row, so this needs no extra query.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailsSheet(
    item: TransactionListItem,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = item.categoryColor?.let { Color(it.toInt()) }?.copy(alpha = 0.18f)
                                ?: MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = item.categoryIcon ?: "🔄", style = MaterialTheme.typography.titleLarge)
                }

                Text(
                    text = item.categoryName ?: stringResource(R.string.transactions_transfer),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )

                Text(
                    text = stringResource(R.string.money_with_currency, signedAmount(item)),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (item.type == TransactionType.INCOME) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            HorizontalDivider()

            DetailRow(
                labelRes = R.string.details_type,
                value = stringResource(
                    when (item.type) {
                        TransactionType.INCOME -> R.string.quick_entry_income
                        TransactionType.EXPENSE -> R.string.quick_entry_expense
                        TransactionType.TRANSFER -> R.string.transactions_transfer
                    },
                ),
            )
            DetailRow(labelRes = R.string.details_date, value = dateLabel(item.dateEpochDay))
            DetailRow(labelRes = R.string.details_account, value = item.accountName)
            item.note?.takeIf { it.isNotBlank() }?.let { note ->
                DetailRow(labelRes = R.string.details_note, value = note)
            }
        }
    }
}

@Composable
private fun DetailRow(
    labelRes: Int,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
