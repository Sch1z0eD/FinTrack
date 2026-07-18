package com.findev.fintrack.ui.screens.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.AccountEntity
import com.findev.fintrack.ui.formatMinor

/** Creates an account, or edits one when [account] is given. */
@Composable
fun AccountDialog(
    account: AccountEntity?,
    /** Computed balance of [account]; shown read-only so the opening figure is not mistaken for it. */
    currentBalanceMinor: Long?,
    onConfirm: (name: String, initialBalanceMinor: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var balance by remember {
        // Whole rubles: the opening balance is a round figure the user types by hand.
        mutableStateOf(account?.let { (it.initialBalanceMinor / 100).toString() } ?: "")
    }

    val trimmedName = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (account == null) R.string.account_create_title else R.string.account_edit_title,
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.account_create_name)) },
                    placeholder = { Text(stringResource(R.string.account_create_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { input -> balance = input.filter { it.isDigit() }.take(9) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.account_create_balance)) },
                    suffix = { Text(stringResource(R.string.money_with_currency, "")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (currentBalanceMinor != null) {
                    Text(
                        text = stringResource(
                            R.string.account_current_balance,
                            stringResource(
                                R.string.money_with_currency,
                                formatMinor(currentBalanceMinor),
                            ),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(R.string.account_initial_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = trimmedName.isNotEmpty(),
                onClick = { onConfirm(trimmedName, (balance.toLongOrNull() ?: 0L) * 100) },
            ) {
                Text(
                    stringResource(
                        if (account == null) R.string.account_create_confirm else R.string.account_save,
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
