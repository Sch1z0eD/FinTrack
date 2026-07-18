package com.findev.fintrack.data

import com.findev.fintrack.data.local.entity.LoanEntity
import com.findev.fintrack.data.local.entity.LoanPrepaymentEntity
import com.findev.fintrack.data.local.entity.LoanRateEntity
import com.findev.fintrack.loanengine.Prepayment
import com.findev.fintrack.loanengine.RateChange
import java.time.LocalDate
import com.findev.fintrack.data.local.entity.LoanType as StoredLoanType
import com.findev.fintrack.data.local.entity.PrepaymentMode as StoredPrepaymentMode
import com.findev.fintrack.loanengine.Loan as EngineLoan
import com.findev.fintrack.loanengine.LoanType as EngineLoanType
import com.findev.fintrack.loanengine.PrepaymentMode as EnginePrepaymentMode

/**
 * The boundary between storage and the engine.
 *
 * Both sides have a LoanType and a PrepaymentMode with the same names on purpose - the
 * engine is a pure module and cannot see the entities. Mapping them by hand (rather than
 * by name) means adding a type on one side breaks the build here, where it is obvious,
 * instead of silently at runtime.
 */

fun LoanEntity.toEngineLoan(): EngineLoan = EngineLoan(
    type = type.toEngine(),
    principalMinor = principalMinor,
    annualRateBp = rateBp,
    startDate = LocalDate.ofEpochDay(startDateEpochDay),
    termMonths = termMonths,
    paymentDay = paymentDay,
    upfrontFeeMinor = upfrontFeeMinor,
    monthlyFeeMinor = monthlyFeeMinor,
)

fun LoanRateEntity.toRateChange(): RateChange = RateChange(
    effectiveFrom = LocalDate.ofEpochDay(effectiveFromEpochDay),
    annualRateBp = rateBp,
)

fun LoanPrepaymentEntity.toPrepayment(): Prepayment = Prepayment(
    date = LocalDate.ofEpochDay(dateEpochDay),
    amountMinor = amountMinor,
    mode = mode.toEngine(),
)

private fun StoredLoanType.toEngine(): EngineLoanType = when (this) {
    StoredLoanType.ANNUITY -> EngineLoanType.ANNUITY
    StoredLoanType.DIFFERENTIATED -> EngineLoanType.DIFFERENTIATED
    StoredLoanType.INSTALLMENT -> EngineLoanType.INSTALLMENT
    StoredLoanType.INTEREST_ONLY -> EngineLoanType.INTEREST_ONLY
}

private fun StoredPrepaymentMode.toEngine(): EnginePrepaymentMode = when (this) {
    StoredPrepaymentMode.REDUCE_TERM -> EnginePrepaymentMode.REDUCE_TERM
    StoredPrepaymentMode.REDUCE_PAYMENT -> EnginePrepaymentMode.REDUCE_PAYMENT
}
