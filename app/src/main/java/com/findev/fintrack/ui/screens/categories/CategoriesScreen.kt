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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.findev.fintrack.ui.RowCorner
import com.findev.fintrack.ui.panelSurface

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // Room for the FAB: without it the last row's "..." sits under the button.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categorySection(
                titleRes = R.string.categories_expense,
                categories = state.expense,
                onEdit = { editing = it },
                onArchiveToggle = { viewModel.onArchiveToggle(it.id, !it.isArchived) },
                onDelete = { viewModel.onDelete(it.id) },
            )
            categorySection(
                titleRes = R.string.categories_income,
                categories = state.income,
                onEdit = { editing = it },
                onArchiveToggle = { viewModel.onArchiveToggle(it.id, !it.isArchived) },
                onDelete = { viewModel.onDelete(it.id) },
            )
        }
    }

    creatingType?.let { type ->
        CategoryDialog(
            category = null,
            initialType = type,
            onConfirm = { name, chosenType, icon, color ->
                viewModel.onCreate(name, chosenType, icon, color)
                creatingType = null
            },
            onDismiss = { creatingType = null },
        )
    }

    editing?.let { category ->
        CategoryDialog(
            category = category,
            initialType = category.type,
            onConfirm = { name, _, icon, color ->
                viewModel.onSaveEdit(category.id, name, icon, color)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.categorySection(
    titleRes: Int,
    categories: List<CategoryEntity>,
    onEdit: (CategoryEntity) -> Unit,
    onArchiveToggle: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
) {
    item(key = "header-$titleRes") {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
    }
    if (categories.isEmpty()) {
        item(key = "empty-$titleRes") {
            Text(
                text = stringResource(R.string.categories_empty_type),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    items(categories, key = { it.id }) { category ->
        CategoryListRow(
            category = category,
            onEdit = { onEdit(category) },
            onArchiveToggle = { onArchiveToggle(category) },
            onDelete = { onDelete(category) },
            // Archiving moves a row between sections; animate rather than teleport.
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun CategoryListRow(
    category: CategoryEntity,
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
