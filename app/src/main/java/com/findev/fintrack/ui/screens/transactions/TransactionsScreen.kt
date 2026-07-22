package com.findev.fintrack.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.ui.UndoSnackbarHost
import com.findev.fintrack.ui.showUndo
import com.findev.fintrack.data.StatPeriod
import com.findev.fintrack.data.local.TransactionListItem
import com.findev.fintrack.data.local.entity.TransactionType
import com.findev.fintrack.ui.PeriodFilterBar
import com.findev.fintrack.ui.RowCorner
import com.findev.fintrack.ui.FilterDropdown
import com.findev.fintrack.ui.dateLabel
import com.findev.fintrack.ui.floatingBottomBarSpace
import com.findev.fintrack.ui.formatMinor
import com.findev.fintrack.ui.theme.MoneyColors
import com.findev.fintrack.ui.screens.statistics.labelRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onEditTransaction: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Tapping a row shows it; changing it is behind the swipe actions.
    var viewing by remember { mutableStateOf<TransactionListItem?>(null) }

    val deletedMessage = stringResource(R.string.transactions_deleted)
    val undoLabel = stringResource(R.string.transactions_undo)

    // The floating bar overlaps the bottom of the screen, so everything anchored there
    // is lifted clear of it by hand.
    val barSpace = floatingBottomBarSpace()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            UndoSnackbarHost(snackbarHostState, bottomPadding = barSpace)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                modifier = Modifier.padding(bottom = barSpace),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.quick_entry_add),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Outside the LazyColumn on purpose: a filter that scrolls away leaves no way
            // to tell a quiet month from a short list without scrolling back up.
            PeriodFilterBar(
                selection = state.selection,
                onPeriodChange = viewModel::onPeriodChange,
                onCustomRangeChange = viewModel::onCustomRangeChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                leading = {
                    // Same line as the period control: two stacked filter rows ate the top
                    // of a screen that exists to show the list below them.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val typeOptions = listOf(
                            TypeFilter.ALL to stringResource(R.string.filter_all),
                            TypeFilter.EXPENSE to stringResource(R.string.filter_expense),
                            TypeFilter.INCOME to stringResource(R.string.filter_income),
                        )
                        FilterDropdown(
                            label = typeOptions.first { it.first == state.typeFilter }.second,
                            options = typeOptions,
                            selected = state.typeFilter,
                            onSelected = viewModel::onTypeFilterChange,
                        )

                        // Only when there is more than one category to choose between - a single
                        // category (or none, on an all-transfer feed) is nothing to filter by.
                        if (state.categoryOptions.size > 1) {
                            val allLabel = stringResource(R.string.filter_category_all)
                            val categoryOptions = listOf<Pair<String?, String>>(null to allLabel) +
                                state.categoryOptions.map { it.id to it.name }
                            val selectedName = state.categoryOptions
                                .firstOrNull { it.id == state.categoryFilter }?.name
                            FilterDropdown(
                                // Truncated so a long category name cannot push the period
                                // control off the row.
                                label = (selectedName ?: allLabel).let {
                                    if (it.length > 14) it.take(13) + "…" else it
                                },
                                options = categoryOptions,
                                selected = state.categoryFilter,
                                onSelected = viewModel::onCategoryFilterChange,
                            )
                        }
                    }
                },
            )

            if (state.isEmpty) {
                EmptyFeed(filtered = state.isFilteredEmpty, modifier = Modifier.weight(1f))
            } else {
                TransactionFeed(
                    state = state,
                    bottomPadding = barSpace,
                    onView = { viewing = it },
                    onEdit = onEditTransaction,
                    onDelete = { id ->
                        viewModel.onDelete(id)
                        scope.launch {
                            if (snackbarHostState.showUndo(deletedMessage, undoLabel)) {
                                viewModel.onUndoDelete(id)
                            }
                        }
                    },
                )
            }
        }
    }

    viewing?.let { item ->
        TransactionDetailsSheet(item = item, onDismiss = { viewing = null })
    }
}

@Composable
private fun EmptyFeed(filtered: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(
                if (filtered) R.string.transactions_empty_period else R.string.transactions_empty,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            // Telling someone to add their first transaction when they have hundreds and
            // merely picked a quiet week is the kind of thing that makes an app feel dumb.
            text = stringResource(
                if (filtered) {
                    R.string.transactions_empty_period_hint
                } else {
                    R.string.transactions_empty_hint
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransactionFeed(
    state: TransactionsUiState,
    bottomPadding: Dp,
    onView: (TransactionListItem) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // 88dp clears the FAB, which now also sits above the floating bar.
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 88.dp + bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.groups.forEach { group ->
            item(key = "header-${group.dateEpochDay}") {
                Text(
                    text = dateLabel(group.dateEpochDay),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(group.items, key = { it.id }) { item ->
                SwipeableTransactionRow(
                    item = item,
                    onClick = { onView(item) },
                    onEdit = { onEdit(item.id) },
                    onDelete = { onDelete(item.id) },
                    // Deleting removes a row mid-list; let the rest slide up.
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private val ACTION_WIDTH = 88.dp

/**
 * Swiping left parks the row open over two actions instead of deleting outright.
 *
 * Not SwipeToDismissBox: that one only dismisses or springs back, it cannot rest
 * half-open. The row follows the finger and settles on the nearer of two anchors.
 */
@Composable
private fun SwipeableTransactionRow(
    item: TransactionListItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val actionsWidthPx = with(LocalDensity.current) { (ACTION_WIDTH * 2).toPx() }

    // Plain state, updated synchronously while dragging. Driving an Animatable from the
    // drag callback instead would race: a late snapTo cancels the settle animation and
    // leaves the row parked wherever the finger happened to stop.
    var offsetX by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun settleTo(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(initialValue = offsetX, targetValue = target) { value, _ -> offsetX = value }
        }
    }

    fun close() = settleTo(0f)

    val draggableState = rememberDraggableState { delta ->
        settleJob?.cancel()
        offsetX = (offsetX + delta).coerceIn(-actionsWidthPx, 0f)
    }

    // Clipping the whole stack keeps the swipe actions inside the card's rounded outline
    // instead of squaring off its corners as they are revealed.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RowCorner)),
    ) {
        // matchParentSize: the actions take their height from the row, never define it.
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End,
        ) {
            RowAction(
                icon = Icons.Filled.Edit,
                labelRes = R.string.transactions_edit,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = {
                    close()
                    onEdit()
                },
            )
            RowAction(
                icon = Icons.Filled.Delete,
                labelRes = R.string.transactions_delete,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {
                    close()
                    onDelete()
                },
            )
        }

        TransactionRow(
            item = item,
            onClick = {
                // An open row swallows the tap: closing it is what the user means.
                if (offsetX != 0f) close() else onClick()
            },
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        // Settle on the nearer anchor: a short drag springs back.
                        settleTo(if (offsetX < -actionsWidthPx / 2) -actionsWidthPx else 0f)
                    },
                ),
        )
    }
}

@Composable
private fun RowAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(ACTION_WIDTH)
            .fillMaxHeight()
            .background(container)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content)
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

@Composable
private fun TransactionRow(
    item: TransactionListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Fully opaque on purpose: it slides over the actions and must hide them, so
            // this one surface cannot join the translucent glass the rest of the app uses.
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = item.categoryColor?.let { Color(it.toInt()) }?.copy(alpha = 0.18f)
                        ?: MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = item.categoryIcon ?: "🔄", style = MaterialTheme.typography.titleMedium)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                // Transfers have no category.
                text = item.categoryName ?: stringResource(R.string.transactions_transfer),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = item.note?.takeIf { it.isNotBlank() } ?: item.accountName
            Text(
                // A part payment looks exactly like any other expense in a list, which is
                // how an obligation can sit half-paid without anything saying so.
                text = if (item.isPartialSettlement) {
                    stringResource(R.string.transactions_partial_suffix, subtitle)
                } else {
                    subtitle
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isPartialSettlement) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = stringResource(R.string.money_with_currency, signedAmount(item)),
            style = MaterialTheme.typography.titleMedium,
            color = when (item.type) {
                TransactionType.INCOME -> MoneyColors.income
                TransactionType.EXPENSE -> MoneyColors.expense
                TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** Amounts are stored unsigned; the sign comes from the type. */
internal fun signedAmount(item: TransactionListItem): String {
    val prefix = when (item.type) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE -> "−"
        TransactionType.TRANSFER -> ""
    }
    return prefix + formatMinor(item.amountMinor)
}
