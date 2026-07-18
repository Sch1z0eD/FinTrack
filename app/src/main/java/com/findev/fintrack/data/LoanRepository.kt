package com.findev.fintrack.data

import com.findev.fintrack.data.local.dao.LoanDao
import com.findev.fintrack.data.local.dao.LoanPrepaymentDao
import com.findev.fintrack.data.local.dao.LoanRateDao
import com.findev.fintrack.data.local.entity.LoanEntity
import com.findev.fintrack.data.local.entity.reminderDaysToStored
import com.findev.fintrack.data.local.entity.LoanPrepaymentEntity
import com.findev.fintrack.data.local.entity.LoanRateEntity
import com.findev.fintrack.data.local.entity.LoanType
import com.findev.fintrack.data.local.entity.PrepaymentMode
import com.findev.fintrack.loanengine.LoanSummary
import com.findev.fintrack.loanengine.ScheduleEntry
import com.findev.fintrack.loanengine.generateSchedule
import com.findev.fintrack.loanengine.summarize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/**
 * A loan together with everything derived from it. The schedule is never stored.
 *
 * The stored prepayments travel alongside their own consequences because the card both
 * lists them (needing their ids to delete them) and simulates new ones against them.
 */
data class LoanWithSchedule(
    val loan: LoanEntity,
    val rates: List<LoanRateEntity>,
    val prepayments: List<LoanPrepaymentEntity>,
    val schedule: List<ScheduleEntry>,
    val summary: LoanSummary,
)

class LoanRepository @Inject constructor(
    private val loanDao: LoanDao,
    private val loanRateDao: LoanRateDao,
    private val loanPrepaymentDao: LoanPrepaymentDao,
) {
    fun observeAll(): Flow<List<LoanEntity>> = loanDao.observeAll()

    suspend fun getById(id: String): LoanEntity? = loanDao.getById(id)

    /**
     * The loan plus its recomputed schedule. Rate changes and prepayments are inputs to
     * the pure engine, so any edit to them re-derives the whole thing - nothing to
     * invalidate, nothing to keep in sync.
     */
    fun observeWithSchedule(loanId: String): Flow<LoanWithSchedule?> = combine(
        loanDao.observeById(loanId),
        loanRateDao.observeByLoan(loanId),
        loanPrepaymentDao.observeByLoan(loanId),
    ) { loan, rates, prepayments ->
        if (loan == null) return@combine null

        val engineLoan = loan.toEngineLoan()
        val schedule = generateSchedule(
            loan = engineLoan,
            rateChanges = rates.map { it.toRateChange() },
            prepayments = prepayments.map { it.toPrepayment() },
        )
        LoanWithSchedule(loan, rates, prepayments, schedule, summarize(engineLoan, schedule))
    }

    /**
     * Every loan with its schedule. Rates and prepayments are fetched in one query each
     * and grouped in memory: a per-loan query would be N+1, and skipping them altogether
     * would quietly report balances that ignore the money already paid early.
     */
    fun observeAllWithSchedules(): Flow<List<LoanWithSchedule>> = combine(
        loanDao.observeAll(),
        loanRateDao.observeAll(),
        loanPrepaymentDao.observeAll(),
    ) { loans, rates, prepayments ->
        val ratesByLoan = rates.groupBy { it.loanId }
        val prepaymentsByLoan = prepayments.groupBy { it.loanId }

        loans.map { loan ->
            val loanRates = ratesByLoan[loan.id].orEmpty()
            val loanPrepayments = prepaymentsByLoan[loan.id].orEmpty()
            val engineLoan = loan.toEngineLoan()
            val schedule = generateSchedule(
                loan = engineLoan,
                rateChanges = loanRates.map { it.toRateChange() },
                prepayments = loanPrepayments.map { it.toPrepayment() },
            )
            LoanWithSchedule(loan, loanRates, loanPrepayments, schedule, summarize(engineLoan, schedule))
        }
    }

    suspend fun create(
        name: String,
        type: LoanType,
        principalMinor: Long,
        rateMilliPercent: Int,
        startDateEpochDay: Long,
        termMonths: Int,
        paymentDay: Int,
        upfrontFeeMinor: Long,
        monthlyFeeMinor: Long,
        accountId: String?,
        categoryId: String?,
        reminderDays: List<Int>,
        firstPaymentEpochDay: Long?,
        fixedPaymentMinor: Long?,
        allowedPrepaymentMode: PrepaymentMode?,
    ): String {
        val id = UUID.randomUUID().toString()
        loanDao.insert(
            LoanEntity(
                id = id,
                name = name,
                type = type,
                principalMinor = principalMinor,
                rateMilliPercent = rateMilliPercent,
                startDateEpochDay = startDateEpochDay,
                termMonths = termMonths,
                paymentDay = paymentDay,
                upfrontFeeMinor = upfrontFeeMinor,
                monthlyFeeMinor = monthlyFeeMinor,
                accountId = accountId,
                categoryId = categoryId,
                reminderDays = reminderDaysToStored(reminderDays),
                firstPaymentEpochDay = firstPaymentEpochDay,
                fixedPaymentMinor = fixedPaymentMinor,
                allowedPrepaymentMode = allowedPrepaymentMode,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun update(
        id: String,
        name: String,
        type: LoanType,
        principalMinor: Long,
        rateMilliPercent: Int,
        startDateEpochDay: Long,
        termMonths: Int,
        paymentDay: Int,
        upfrontFeeMinor: Long,
        monthlyFeeMinor: Long,
        accountId: String?,
        categoryId: String?,
        reminderDays: List<Int>,
        firstPaymentEpochDay: Long?,
        fixedPaymentMinor: Long?,
        allowedPrepaymentMode: PrepaymentMode?,
    ) {
        val existing = requireNotNull(loanDao.getById(id)) { "No loan with id $id" }
        loanDao.update(
            existing.copy(
                name = name,
                type = type,
                principalMinor = principalMinor,
                rateMilliPercent = rateMilliPercent,
                startDateEpochDay = startDateEpochDay,
                termMonths = termMonths,
                paymentDay = paymentDay,
                upfrontFeeMinor = upfrontFeeMinor,
                monthlyFeeMinor = monthlyFeeMinor,
                accountId = accountId,
                categoryId = categoryId,
                reminderDays = reminderDaysToStored(reminderDays),
                firstPaymentEpochDay = firstPaymentEpochDay,
                fixedPaymentMinor = fixedPaymentMinor,
                allowedPrepaymentMode = allowedPrepaymentMode,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: String) = loanDao.softDelete(id, System.currentTimeMillis())


    suspend fun addPrepayment(
        loanId: String,
        amountMinor: Long,
        dateEpochDay: Long,
        mode: PrepaymentMode,
    ) {
        loanPrepaymentDao.insert(
            LoanPrepaymentEntity(
                id = UUID.randomUUID().toString(),
                loanId = loanId,
                amountMinor = amountMinor,
                dateEpochDay = dateEpochDay,
                mode = mode,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deletePrepayment(id: String) =
        loanPrepaymentDao.softDelete(id, System.currentTimeMillis())

    /**
     * Records that the rate changed from [effectiveFromEpochDay] onwards.
     *
     * The table, the engine and the schedule have supported this from the start - only the
     * way in was missing, which made a two-rate contract impossible to enter correctly. A
     * Yandex Bank loan that steps from 70% to 28.572% came out 57 140 rub over its real
     * total, because the app had no way to be told about the second rate.
     */
    suspend fun addRateChange(loanId: String, rateMilliPercent: Int, effectiveFromEpochDay: Long) {
        loanRateDao.insert(
            LoanRateEntity(
                id = UUID.randomUUID().toString(),
                loanId = loanId,
                rateMilliPercent = rateMilliPercent,
                effectiveFromEpochDay = effectiveFromEpochDay,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteRateChange(id: String) =
        loanRateDao.softDelete(id, System.currentTimeMillis())
}
