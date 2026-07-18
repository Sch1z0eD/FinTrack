package com.findev.fintrack.data

import com.findev.fintrack.data.local.dao.AccountDao
import com.findev.fintrack.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

/** Deleting an account that still has transactions would leave them orphaned. */
class AccountHasTransactionsException : IllegalStateException("Account still has transactions")

class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
) {
    /** Archived included - for the accounts screen and balances. */
    fun observeAll(): Flow<List<AccountEntity>> = accountDao.observeAll()

    /** For picking an account when entering a transaction. */
    fun observeActive(): Flow<List<AccountEntity>> = accountDao.observeActive()

    /** @return id of the created account, so callers can select it right away. */
    suspend fun create(name: String, initialBalanceMinor: Long): String {
        val id = UUID.randomUUID().toString()
        accountDao.insert(
            AccountEntity(
                id = id,
                name = name,
                initialBalanceMinor = initialBalanceMinor,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun rename(id: String, name: String, initialBalanceMinor: Long) {
        val existing = requireNotNull(accountDao.getById(id)) { "No account with id $id" }
        accountDao.update(
            existing.copy(
                name = name,
                initialBalanceMinor = initialBalanceMinor,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        val existing = requireNotNull(accountDao.getById(id)) { "No account with id $id" }
        accountDao.update(
            existing.copy(isArchived = archived, updatedAt = System.currentTimeMillis()),
        )
    }

    suspend fun canDelete(id: String): Boolean = accountDao.countTransactions(id) == 0

    /**
     * Only an account without transactions can be deleted; anything else must be archived,
     * otherwise its transactions would still count towards the total balance while the
     * account itself disappeared from the per-account list.
     */
    suspend fun delete(id: String) {
        if (!canDelete(id)) throw AccountHasTransactionsException()
        accountDao.softDelete(id, System.currentTimeMillis())
    }
}
