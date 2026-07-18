package com.findev.fintrack.ui.screens.utilities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.AccountRepository
import com.findev.fintrack.data.CategoryRepository
import com.findev.fintrack.data.MeterRepository
import com.findev.fintrack.data.MonthlyConsumption
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.monthlyChargeMinor
import com.findev.fintrack.data.local.entity.AccountEntity
import com.findev.fintrack.data.local.entity.CategoryType
import com.findev.fintrack.data.local.entity.MeterEntity
import com.findev.fintrack.data.local.entity.MeterReadingEntity
import com.findev.fintrack.data.local.entity.TransactionType
import com.findev.fintrack.data.monthlyConsumption
import com.findev.fintrack.ui.formatAmountForInput
import com.findev.fintrack.ui.navigation.METER_DETAIL_ARG_ID
import com.findev.fintrack.ui.parseAmountToMinor
import com.findev.fintrack.ui.sanitizeAmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** A reading plus whether an expense already settles it. */
data class ReadingItem(
    val reading: MeterReadingEntity,
    val isPaid: Boolean,
)

data class MeterDetailUiState(
    val meter: MeterEntity? = null,
    val readings: List<ReadingItem> = emptyList(),
    val months: List<MonthlyConsumption> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    /** True when a non-metered service's charge for the current month is already booked. */
    val monthPaidThisMonth: Boolean = false,
) {
    /** What a non-metered service costs every month; null when it is billed by readings. */
    val monthlyChargeMinor: Long? get() = meter?.monthlyChargeMinor()
}

/**
 * The "Оплатить" confirmation for a utility charge.
 *
 * The amount is editable and prefilled, like every settlement in the app: what was actually
 * paid can differ from what was computed. [settlesId] is the reading's id for a metered
 * charge, or the meter's id for a normative month - both are UUIDs, so the same
 * settles_payment_id column tells the two apart by which key it holds.
 */
data class MeterPayDialogState(
    val settlesId: String,
    /** The reading's date, or the first of the month for a normative charge. */
    val dueEpochDay: Long,
    val note: String,
    val categoryId: String,
    val accountId: String,
    val amountText: String,
    val dateEpochDay: Long,
) {
    val amountMinor: Long get() = parseAmountToMinor(amountText)
    val canSave: Boolean get() = amountMinor > 0 && accountId.isNotEmpty() && categoryId.isNotEmpty()
}

/** The expense just booked, kept only long enough to offer taking it back. */
data class MeterPaidUndo(val transactionId: String)

/**
 * Books a utility charge as the expense that settles it, in the ЖКХ category.
 *
 * An extension rather than a repository method: the dialog is a UI shape, and "paid" is
 * never stored - it is only ever this transaction, so deleting it walks the charge back to
 * unpaid on its own.
 */
suspend fun TransactionRepository.settle(dialog: MeterPayDialogState): String = addIncomeOrExpense(
    type = TransactionType.EXPENSE,
    amountMinor = dialog.amountMinor,
    accountId = dialog.accountId,
    categoryId = dialog.categoryId,
    dateEpochDay = dialog.dateEpochDay,
    note = dialog.note,
    settlesPaymentId = dialog.settlesId,
    settlesDueEpochDay = dialog.dueEpochDay,
)

@HiltViewModel
class MeterDetailViewModel @Inject constructor(
    private val meterRepository: MeterRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val meterId: String = requireNotNull(savedStateHandle[METER_DETAIL_ARG_ID])

    private val meterFlow = MutableStateFlow<MeterEntity?>(null)

    /** The ЖКХ category, if it still exists - matched by its seeded icon so a rename keeps it. */
    private var utilitiesCategoryId: String? = null

    private val _payDialog = MutableStateFlow<MeterPayDialogState?>(null)
    val payDialog: StateFlow<MeterPayDialogState?> = _payDialog.asStateFlow()

    private val _paidUndo = MutableStateFlow<MeterPaidUndo?>(null)
    val paidUndo: StateFlow<MeterPaidUndo?> = _paidUndo.asStateFlow()

    val uiState: StateFlow<MeterDetailUiState> = combine(
        meterFlow.asStateFlow(),
        meterRepository.observeReadings(meterId),
        accountRepository.observeActive(),
        transactionRepository.observePaidThrough(),
    ) { meter, readings, accounts, paidThrough ->
        // A metered charge is settled when a transaction points at that reading's id; a
        // monthly service (norm or fixed) is settled when the meter's latest settled due
        // lands in this month.
        val monthPaid = meter?.monthlyChargeMinor() != null &&
            paidThrough[meter.id]?.let { YearMonth.from(LocalDate.ofEpochDay(it)) == YearMonth.now() } == true

        MeterDetailUiState(
            meter = meter,
            readings = readings.map { ReadingItem(it, paidThrough.containsKey(it.id)) },
            months = monthlyConsumption(readings),
            accounts = accounts,
            monthPaidThisMonth = monthPaid,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MeterDetailUiState(),
    )

    init {
        refresh()
        viewModelScope.launch {
            utilitiesCategoryId = categoryRepository.utilitiesCategoryId()
        }
    }

    /** The meter itself has no Flow to observe, so it is re-read when the screen resumes. */
    fun refresh() {
        viewModelScope.launch { meterFlow.value = meterRepository.getById(meterId) }
    }

    fun onDeleteReading(id: String) {
        viewModelScope.launch { meterRepository.deleteReading(id) }
    }

    fun onPayReadingClick(item: ReadingItem) {
        openPayDialog(
            settlesId = item.reading.id,
            dueEpochDay = item.reading.dateEpochDay,
            amountMinor = item.reading.amountMinor,
        )
    }

    fun onPayMonthClick() {
        val meter = uiState.value.meter ?: return
        val charge = uiState.value.monthlyChargeMinor ?: return
        // The month being paid is keyed by its first day, so each month settles distinctly
        // under the meter's own id.
        openPayDialog(
            settlesId = meter.id,
            dueEpochDay = LocalDate.now().withDayOfMonth(1).toEpochDay(),
            amountMinor = charge,
        )
    }

    private fun openPayDialog(settlesId: String, dueEpochDay: Long, amountMinor: Long) {
        val meter = uiState.value.meter ?: return
        val accountId = uiState.value.accounts.firstOrNull()?.id ?: return
        val categoryId = utilitiesCategoryId ?: return

        _payDialog.value = MeterPayDialogState(
            settlesId = settlesId,
            dueEpochDay = dueEpochDay,
            note = meter.name,
            categoryId = categoryId,
            accountId = accountId,
            amountText = formatAmountForInput(amountMinor),
            dateEpochDay = LocalDate.now().toEpochDay(),
        )
    }

    fun onPayAmountChange(text: String) = _payDialog.update {
        it?.copy(amountText = sanitizeAmountInput(text))
    }

    fun onPayAccountChange(accountId: String) = _payDialog.update { it?.copy(accountId = accountId) }

    fun onPayDateChange(epochDay: Long) = _payDialog.update { it?.copy(dateEpochDay = epochDay) }

    fun onPayDismiss() {
        _payDialog.value = null
    }

    fun onPayConfirm() {
        val dialog = _payDialog.value ?: return
        if (!dialog.canSave) return
        _payDialog.value = null

        viewModelScope.launch {
            val transactionId = transactionRepository.settle(dialog)
            _paidUndo.value = MeterPaidUndo(transactionId)
        }
    }

    /** Deleting the expense is all undo needs to be: the mark was never separate from it. */
    fun onUndoPaid() {
        val undo = _paidUndo.value ?: return
        _paidUndo.value = null
        viewModelScope.launch { transactionRepository.softDelete(undo.transactionId) }
    }

    fun onUndoDismissed() {
        _paidUndo.value = null
    }
}
