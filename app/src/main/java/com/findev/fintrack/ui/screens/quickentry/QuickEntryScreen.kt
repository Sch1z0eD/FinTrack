package com.findev.fintrack.ui.screens.quickentry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.data.local.entity.CategoryEntity
import com.findev.fintrack.data.local.entity.TransactionType
import com.findev.fintrack.ui.dateLabel
import com.findev.fintrack.ui.formatMinor

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
fun QuickEntryScreen(
    onDone: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: QuickEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onDone() }
    }

    QuickEntryContent(
        state = state,
        onAmountTextChange = viewModel::onAmountTextChange,
        onTypeChange = viewModel::onTypeChange,
        onAccountSelected = viewModel::onAccountSelected,
        onCategorySelected = viewModel::onCategorySelected,
        onDateSelected = viewModel::onDateSelected,
        onNoteChange = viewModel::onNoteChange,
        onOpenAccounts = onOpenAccounts,
        onOpenCategories = onOpenCategories,
        onSave = viewModel::onSave,
        onBack = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickEntryContent(
    state: QuickEntryUiState,
    onAmountTextChange: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onAccountSelected: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onDateSelected: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var noteVisible by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val noteFocusRequester = remember { FocusRequester() }

    // One-shot: an edited transaction may already carry a comment. After this, only the
    // chip decides visibility - an effect watching the text would override the user's
    // explicit "убрать" the moment anything typed a value back.
    var noteInitialised by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoaded) {
        if (state.isLoaded && !noteInitialised) {
            noteVisible = state.note.isNotEmpty()
            noteInitialised = true
        }
    }

    Scaffold(
        // FinTrackApp's Scaffold already inset for the status bar; insetting again
        // would leave an empty grey strip above the title.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.quick_entry_title_edit else R.string.quick_entry_title,
                        ),
                    )
                },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The keyboard only opens on tap, so the category grid stays fully visible
            // until the amount is actually being typed.
            OutlinedTextField(
                value = state.amountText,
                onValueChange = onAmountTextChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.End),
                placeholder = {
                    Text(
                        text = "0,00",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                suffix = { Text("₽", style = MaterialTheme.typography.headlineSmall) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            TypeSelector(selected = state.type, onTypeChange = onTypeChange)

            AccountRow(
                state = state,
                onAccountSelected = onAccountSelected,
                onOpenAccounts = onOpenAccounts,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {
                        focusManager.clearFocus()
                        showDatePicker = true
                    },
                    label = { Text(dateLabel(state.dateEpochDay)) },
                )

                // No permanent field: the comment is asked for only when wanted.
                AssistChip(
                    onClick = {
                        if (noteVisible) {
                            // Hiding means "no comment", so the text must not be saved unseen.
                            onNoteChange("")
                            focusManager.clearFocus()
                            noteVisible = false
                        } else {
                            noteVisible = true
                        }
                    },
                    label = {
                        Text(
                            stringResource(
                                if (noteVisible) R.string.quick_entry_note_remove else R.string.quick_entry_note,
                            ),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (noteVisible) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = null,
                        )
                    },
                )
            }

            if (noteVisible) {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNoteChange,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.quick_entry_note)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(noteFocusRequester),
                )
            }

            // Opening the comment should land the cursor there straight away.
            LaunchedEffect(noteVisible) {
                if (noteVisible && state.note.isEmpty()) noteFocusRequester.requestFocus()
            }

            CategoryGrid(
                categories = state.categories,
                selectedCategoryId = state.selectedCategoryId,
                onCategorySelected = onCategorySelected,
                onOpenCategories = onOpenCategories,
                modifier = Modifier.weight(1f),
            )

            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Text(stringResource(R.string.quick_entry_save))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.dateEpochDay * MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onDateSelected(it / MILLIS_PER_DAY) }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.quick_entry_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.account_create_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSelector(
    selected: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
) {
    val options = listOf(
        TransactionType.EXPENSE to R.string.quick_entry_expense,
        TransactionType.INCOME to R.string.quick_entry_income,
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, labelRes) ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onTypeChange(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}

@Composable
private fun AccountRow(
    state: QuickEntryUiState,
    onAccountSelected: (String) -> Unit,
    onOpenAccounts: () -> Unit,
) {
    // Accounts are managed on their own screen; entry only picks between them.
    if (state.hasNoAccounts) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.quick_entry_no_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistChip(
                onClick = onOpenAccounts,
                label = { Text(stringResource(R.string.quick_entry_no_account_action)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.accounts.forEach { account ->
            FilterChip(
                selected = account.id == state.selectedAccountId,
                onClick = { onAccountSelected(account.id) },
                label = { Text(account.name) },
            )
        }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedCategoryId: String?,
    onCategorySelected: (String) -> Unit,
    onOpenCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        // 5 columns keeps the common expense categories reachable without scrolling.
        columns = GridCells.Fixed(5),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            CategoryCell(
                category = category,
                selected = category.id == selectedCategoryId,
                onClick = { onCategorySelected(category.id) },
            )
        }
        // Managing categories lives on its own screen; this is just the way in.
        item(key = "manage-categories") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable(onClick = onOpenCategories)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.categories_add),
                )
                Text(
                    text = stringResource(R.string.categories_add),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CategoryCell(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = Color(category.color.toInt())
    val shape = RoundedCornerShape(12.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = if (selected) 0.30f else 0.12f))
            .then(
                if (selected) Modifier.border(2.dp, accent, shape) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Text(text = category.icon, style = MaterialTheme.typography.titleLarge)
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

