package com.findev.fintrack.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.findev.fintrack.R

/**
 * A single confirmation before an irreversible delete.
 *
 * One helper rather than a copy per screen: recurring payments, meters, groups, categories
 * and accounts all delete outright with no undo, so each needs the same "are you sure" gate.
 * Transactions are deliberately not routed here - they soft-delete with an undo snackbar,
 * which is the safety net a modal would only get in the way of.
 *
 * [confirmLabel] defaults to the shared «Удалить». The confirm action is tinted with the
 * error colour to mark it destructive, and dismissing is the safe default.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.accounts_delete),
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.account_create_cancel))
            }
        },
    )
}
