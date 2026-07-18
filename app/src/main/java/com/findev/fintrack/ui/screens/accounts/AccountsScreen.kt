package com.findev.fintrack.ui.screens.accounts

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.AccountEntity
import com.findev.fintrack.ui.AppMenu
import com.findev.fintrack.ui.formatMinor
import com.findev.fintrack.ui.RowCorner
import com.findev.fintrack.ui.panelSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // The whole row, not just the account: the dialog also shows the computed balance.
    var editing by remember { mutableStateOf<AccountRow?>(null) }
    var creating by remember { mutableStateOf(false) }

    val blockedMessage = stringResource(R.string.accounts_delete_blocked)
    LaunchedEffect(Unit) {
        viewModel.deleteBlocked.collect { snackbarHostState.showSnackbar(blockedMessage) }
    }

    Scaffold(
        // The app-level Scaffold already inset for the status bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.accounts_title)) },
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
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.account_add))
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isEmpty) {
                EmptyAccounts()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Room for the FAB: without it the last row's "..." sits under the button.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.rows, key = { it.account.id }) { row ->
                        AccountListRow(
                            row = row,
                            onEdit = { editing = row },
                            onArchiveToggle = {
                                viewModel.onArchiveToggle(row.account.id, !row.account.isArchived)
                            },
                            onDelete = { viewModel.onDelete(row.account.id) },
                            // Archiving reorders the list; without this rows teleport.
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        AccountDialog(
            account = null,
            currentBalanceMinor = null,
            onConfirm = { name, balance ->
                viewModel.onCreate(name, balance)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }

    editing?.let { row ->
        AccountDialog(
            account = row.account,
            currentBalanceMinor = row.balanceMinor,
            onConfirm = { name, balance ->
                viewModel.onSaveEdit(row.account.id, name, balance)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun EmptyAccounts() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.accounts_empty),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.accounts_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountListRow(
    row: AccountRow,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    // Archived accounts are out of the balance, so they read back visually too.
    val fade by animateFloatAsState(
        targetValue = if (row.account.isArchived) 0.55f else 1f,
        label = "accountFade",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .panelSurface(RoundedCornerShape(RowCorner))
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp)
            .graphicsLayer { alpha = fade },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.account.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.account.isArchived) {
                Text(
                    text = stringResource(R.string.accounts_archived),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringResource(R.string.money_with_currency, formatMinor(row.balanceMinor)),
            style = MaterialTheme.typography.titleMedium,
        )

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
                                if (row.account.isArchived) {
                                    R.string.accounts_unarchive
                                } else {
                                    R.string.accounts_archive
                                },
                            ),
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onArchiveToggle()
                    },
                )
                // Destructive item carries the warning colour; the icon set bundled here
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
