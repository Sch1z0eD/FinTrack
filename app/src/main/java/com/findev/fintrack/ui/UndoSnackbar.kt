package com.findev.fintrack.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

private const val UNDO_TIMEOUT_MILLIS = 4_000L

/**
 * The undo bar, styled and anchored in one place.
 *
 * It replaced three copies that each got the same two things wrong:
 *
 *  - **It never went away.** showSnackbar switches its default duration to Indefinite the
 *    moment an actionLabel is passed. Even set to Short it is not fully under our control -
 *    Compose runs the timeout through the accessibility manager, which can extend it
 *    indefinitely on a device configured for longer action timeouts. So the timeout is
 *    enforced here instead: Indefinite plus withTimeoutOrNull, which cancels the call and
 *    takes the bar down with it.
 *
 *  - **It rode the keyboard up.** The window is adjustResize, so while an input is focused
 *    the whole layout shrinks and anything anchored to the bottom lands halfway up the
 *    screen. [showUndo] therefore only runs once the caller has dropped focus.
 */
@Composable
fun UndoSnackbarHost(
    hostState: SnackbarHostState,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(bottom = bottomPadding),
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(RowCorner),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}

/**
 * Shows the bar and answers whether the action was pressed.
 *
 * Returns false both when it timed out and when it was swiped away - in either case the
 * user did not ask to undo, which is all the caller needs to know.
 */
suspend fun SnackbarHostState.showUndo(
    message: String,
    actionLabel: String,
    timeoutMillis: Long = UNDO_TIMEOUT_MILLIS,
): Boolean {
    val result = withTimeoutOrNull(timeoutMillis) {
        showSnackbar(
            message = message,
            actionLabel = actionLabel,
            // Ours to time, not Material's - see the note on UndoSnackbarHost.
            duration = SnackbarDuration.Indefinite,
        )
    }
    return result == SnackbarResult.ActionPerformed
}
