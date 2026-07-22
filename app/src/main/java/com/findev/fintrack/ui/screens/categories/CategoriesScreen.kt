package com.findev.fintrack.ui.screens.categories

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.data.local.entity.CategoryType
import com.findev.fintrack.ui.AppMenu
import com.findev.fintrack.ui.ConfirmDeleteDialog
import com.findev.fintrack.ui.DraggableItem
import com.findev.fintrack.ui.dragContainer
import com.findev.fintrack.ui.formatMinor
import com.findev.fintrack.ui.rememberDragDropState
import com.findev.fintrack.ui.RowCorner
import com.findev.fintrack.ui.panelSurface
import com.findev.fintrack.ui.theme.BudgetColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creatingType by remember { mutableStateOf<CategoryType?>(null) }
    var pendingDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    val blockedMessage = stringResource(R.string.categories_delete_blocked)
    LaunchedEffect(Unit) {
        viewModel.deleteBlocked.collect { snackbarHostState.showSnackbar(blockedMessage) }
    }

    Scaffold(
        // The app-level Scaffold already inset for the status bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.quick_entry_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creatingType = CategoryType.EXPENSE }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.categories_add))
            }
        },
    ) { innerPadding ->
        // A local copy the drag reorders optimistically; it tracks the source lists whenever a
        // drag is not in progress, and the new order is persisted per type on drop.
        var localRows by remember { mutableStateOf(emptyList<CatRow>()) }
        val listState = rememberLazyListState()
        val dragState = rememberDragDropState(listState) { from, to ->
            val fromRow = localRows.getOrNull(from) as? CatRow.Item ?: return@rememberDragDropState
            val toRow = localRows.getOrNull(to) as? CatRow.Item ?: return@rememberDragDropState
            // Expense and income are separate grids; a header sits between them as a barrier.
            if (fromRow.category.type != toRow.category.type) return@rememberDragDropState
            localRows = localRows.toMutableList().apply { add(to, removeAt(from)) }
        }
        LaunchedEffect(state.expense, state.income) {
            if (dragState.draggingItemIndex == null) {
                localRows = buildCategoryRows(state.expense, state.income)
            }
        }
        val persistOrder by rememberUpdatedState {
            // Each type's categories, in their current on-screen order.
            CategoryType.entries.forEach { type ->
                viewModel.onReorder(
                    localRows.filterIsInstance<CatRow.Item>()
                        .filter { it.category.type == type }
                        .map { it.category.id },
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .dragContainer(dragState, onDragEnd = { persistOrder() }),
            // Room for the FAB: without it the last row's "..." sits under the button.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(localRows, key = { _, row -> row.key }) { index, row ->
                when (row) {
                    is CatRow.Header -> Text(
                        text = stringResource(row.titleRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    is CatRow.Empty -> Text(
                        text = stringResource(R.string.categories_empty_type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    is CatRow.Item -> DraggableItem(dragState, index) { isDragging ->
                        CategoryListRow(
                            category = row.category,
                            spentThisMonthMinor = row.spentThisMonthMinor,
                            onEdit = { editing = row.category },
                            onArchiveToggle = {
                                viewModel.onArchiveToggle(row.category.id, !row.category.isArchived)
                            },
                            onDelete = { pendingDelete = row.category },
                            // The lifted row gets a shadow so it reads as picked up.
                            modifier = Modifier.shadow(
                                elevation = if (isDragging) 8.dp else 0.dp,
                                shape = RoundedCornerShape(RowCorner),
                            ),
                        )
                    }
                }
            }
        }
    }

    creatingType?.let { type ->
        CategoryDialog(
            category = null,
            initialType = type,
            onConfirm = { name, chosenType, icon, color, limit ->
                viewModel.onCreate(name, chosenType, icon, color, limit)
                creatingType = null
            },
            onDismiss = { creatingType = null },
        )
    }

    editing?.let { category ->
        CategoryDialog(
            category = category,
            initialType = category.type,
            onConfirm = { name, _, icon, color, limit ->
                viewModel.onSaveEdit(category.id, name, icon, color, limit)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    pendingDelete?.let { category ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.categories_delete_title),
            message = stringResource(R.string.categories_delete_confirm, category.name),
            onConfirm = { viewModel.onDelete(category.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** One row of the categories list: a section header, an empty-section note, or a category. */
private sealed interface CatRow {
    val key: Any

    data class Header(val titleRes: Int) : CatRow {
        override val key get() = "header-$titleRes"
    }

    data class Empty(val titleRes: Int) : CatRow {
        override val key get() = "empty-$titleRes"
    }

    /** [spentThisMonthMinor] drives the budget bar; it is 0 for income rows, which have no budget. */
    data class Item(val category: CategoryEntity, val spentThisMonthMinor: Long) : CatRow {
        override val key get() = category.id
    }
}

/** Flattens the two typed lists into one drag-friendly sequence: header, its rows, next header… */
private fun buildCategoryRows(
    expense: List<CategoryRow>,
    income: List<CategoryEntity>,
): List<CatRow> = buildList {
    add(CatRow.Header(R.string.categories_expense))
    if (expense.isEmpty()) add(CatRow.Empty(R.string.categories_expense))
    else expense.forEach { add(CatRow.Item(it.category, it.spentThisMonthMinor)) }
    add(CatRow.Header(R.string.categories_income))
    if (income.isEmpty()) add(CatRow.Empty(R.string.categories_income))
    else income.forEach { add(CatRow.Item(it, 0L)) }
}

@Composable
private fun CategoryListRow(
    category: CategoryEntity,
    spentThisMonthMinor: Long,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    // Archived categories are hidden from quick entry, so they read back here too.
    val fade by animateFloatAsState(
        targetValue = if (category.isArchived) 0.55f else 1f,
        label = "categoryFade",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .panelSurface(RoundedCornerShape(RowCorner))
            .clickable(onClick = onEdit)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
            .graphicsLayer { alpha = fade },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(category.color.toInt()).copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = category.icon, style = MaterialTheme.typography.titleMedium)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (category.isArchived) {
                Text(
                    text = stringResource(R.string.categories_archived),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val limit = category.monthlyLimitMinor
            if (limit != null && limit > 0) {
                // Warn as the month's spending nears (>=80%, amber) and passes the limit (red).
                val over = spentThisMonthMinor > limit
                val near = !over && spentThisMonthMinor >= limit * 8 / 10
                val barColor = when {
                    over -> MaterialTheme.colorScheme.error
                    near -> BudgetColors.warn
                    else -> MaterialTheme.colorScheme.primary
                }
                LinearProgressIndicator(
                    progress = { (spentThisMonthMinor.toFloat() / limit).coerceIn(0f, 1f) },
                    color = barColor,
                    trackColor = barColor.copy(alpha = 0.18f),
                    // No gap or end dot: a plain filled bar reads cleaner at this small size.
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    // Trailing space keeps the bar clear of the "..." button.
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, end = 4.dp)
                        .height(6.dp)
                        .clip(CircleShape),
                )
                Text(
                    text = stringResource(
                        R.string.categories_budget_progress,
                        stringResource(R.string.money_with_currency, formatMinor(spentThisMonthMinor)),
                        stringResource(R.string.money_with_currency, formatMinor(limit)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.accounts_actions),
                )
            }
            AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.accounts_edit)) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (category.isArchived) {
                                    R.string.categories_unarchive
                                } else {
                                    R.string.categories_archive
                                },
                            ),
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onArchiveToggle()
                    },
                )
                // Destructive item carries the warning colour; the bundled icon set
                // (material-icons-core) has no matching glyph, so colour does the work.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.accounts_delete)) },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                    ),
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}
