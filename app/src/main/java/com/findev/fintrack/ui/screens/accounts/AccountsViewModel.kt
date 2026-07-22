package com.findev.fintrack.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.AccountHasTransactionsException
import com.findev.fintrack.data.AccountRepository
import com.findev.fintrack.data.OverviewRepository
import com.findev.fintrack.data.local.AccountBalance
import com.findev.fintrack.data.local.entity.AccountEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountRow(
    val account: AccountEntity,
    val balanceMinor: Long,
)

data class AccountsUiState(
    val rows: List<AccountRow> = emptyList(),
    val isLoaded: Boolean = false,
) {
    val isEmpty: Boolean get() = isLoaded && rows.isEmpty()
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    overviewRepository: OverviewRepository,
) : ViewModel() {

    private val deleteBlockedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits when a delete was refused because the account still has transactions. */
    val deleteBlocked: Flow<Unit> = deleteBlockedChannel.receiveAsFlow()

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.observeAll(),
        overviewRepository.observeAccountBalances(),
    ) { accounts, balances ->
        val balanceById: Map<String, AccountBalance> = balances.associateBy { it.id }
        AccountsUiState(
            rows = accounts.map { account ->
                AccountRow(
                    account = account,
                    balanceMinor = balanceById[account.id]?.balanceMinor ?: account.initialBalanceMinor,
                )
            },
            isLoaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountsUiState(),
    )

    fun onCreate(name: String, initialBalanceMinor: Long) {
        viewModelScope.launch { accountRepository.create(name, initialBalanceMinor) }
    }

    fun onSaveEdit(id: String, name: String, initialBalanceMinor: Long) {
        viewModelScope.launch { accountRepository.rename(id, name, initialBalanceMinor) }
    }

    fun onArchiveToggle(id: String, archived: Boolean) {
        viewModelScope.launch { accountRepository.setArchived(id, archived) }
    }

    fun onDelete(id: String) {
        viewModelScope.launch {
            try {
                accountRepository.delete(id)
            } catch (_: AccountHasTransactionsException) {
                deleteBlockedChannel.send(Unit)
            }
        }
    }

    /** Persists a new account order after a drag; [orderedIds] is the full list, top to bottom. */
    fun onReorder(orderedIds: List<String>) {
        viewModelScope.launch { accountRepository.reorder(orderedIds) }
    }
}
