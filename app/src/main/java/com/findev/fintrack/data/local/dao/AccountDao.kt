package com.findev.fintrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.findev.fintrack.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    /** Everything the user still owns, archived included. */
    @Query("SELECT * FROM account WHERE is_deleted = 0 ORDER BY is_archived, name")
    fun observeAll(): Flow<List<AccountEntity>>

    /** Only accounts that may receive new transactions. */
    @Query("SELECT * FROM account WHERE is_deleted = 0 AND is_archived = 0 ORDER BY name")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    /**
     * Only live transactions block deletion: if the user deleted them, the account is
     * empty as far as they can see. Restoring a soft-deleted one onto a deleted account
     * is prevented in TransactionDao.restore instead.
     */
    @Query(
        "SELECT COUNT(*) FROM transactions WHERE is_deleted = 0 AND (account_id = :id OR account_to_id = :id)",
    )
    suspend fun countTransactions(id: String): Int

    @Query("UPDATE account SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)
}
