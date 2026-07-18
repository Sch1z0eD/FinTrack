package com.findev.fintrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.findev.fintrack.data.local.entity.LoanPrepaymentEntity
import com.findev.fintrack.data.local.entity.LoanRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanRateDao {

    @Query(
        "SELECT * FROM loan_rate WHERE loan_id = :loanId AND is_deleted = 0 ORDER BY effective_from_epoch_day",
    )
    fun observeByLoan(loanId: String): Flow<List<LoanRateEntity>>

    /** Every loan's rates at once: the list screen needs them all and must not run N+1 queries. */
    @Query("SELECT * FROM loan_rate WHERE is_deleted = 0 ORDER BY effective_from_epoch_day")
    fun observeAll(): Flow<List<LoanRateEntity>>

    @Insert
    suspend fun insert(rate: LoanRateEntity)

    @Query("UPDATE loan_rate SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)
}

@Dao
interface LoanPrepaymentDao {

    @Query(
        "SELECT * FROM loan_prepayment WHERE loan_id = :loanId AND is_deleted = 0 ORDER BY date_epoch_day",
    )
    fun observeByLoan(loanId: String): Flow<List<LoanPrepaymentEntity>>

    /** Every loan's prepayments at once, for the same reason as the rates above. */
    @Query("SELECT * FROM loan_prepayment WHERE is_deleted = 0 ORDER BY date_epoch_day")
    fun observeAll(): Flow<List<LoanPrepaymentEntity>>

    @Insert
    suspend fun insert(prepayment: LoanPrepaymentEntity)

    @Query("UPDATE loan_prepayment SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)
}
