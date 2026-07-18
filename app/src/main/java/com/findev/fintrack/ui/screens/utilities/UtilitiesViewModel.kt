package com.findev.fintrack.ui.screens.utilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.AccountRepository
import com.findev.fintrack.data.CategoryRepository
import com.findev.fintrack.data.MeterGroupRepository
import com.findev.fintrack.data.MeterRepository
import com.findev.fintrack.data.TransactionRepository
import com.findev.fintrack.data.chargeMinor
import com.findev.fintrack.data.combinedChargeMinor
import com.findev.fintrack.data.monthlyChargeMinor
import com.findev.fintrack.data.local.entity.AccountEntity
import com.findev.fintrack.data.local.entity.MeterEntity
import com.findev.fintrack.data.local.entity.MeterGroupEntity
import com.findev.fintrack.data.local.entity.MeterReadingEntity
import com.findev.fintrack.data.local.entity.TransactionType
import com.findev.fintrack.ui.parseMeterToMilli
import com.findev.fintrack.ui.sanitizeMeterInput
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

/**
 * A meter, the last thing it was read at, and what there is to pay for it this month.
 *
 * [settlesId]/[settlesDueEpochDay] are what a payment would point at - a metered charge is
 * keyed by its reading, a monthly one by the meter and the first of the month - so [isPaid]
 * can be read straight off the live transactions and a batch payment knows what to settle.
 */
data class MeterItem(
    val meter: MeterEntity,
    val lastReading: MeterReadingEntity?,
    val amountMinor: Long = 0,
    val isPaid: Boolean = false,
    val settlesId: String? = null,
    val settlesDueEpochDay: Long? = null,
) {
    /** Something is owed and not yet settled - so it can be ticked to pay. */
    val isPayable: Boolean get() = settlesId != null && !isPaid && amountMinor > 0
}

/** One group of services with its combined monthly total; [group] is null for «Прочее». */
data class MeterGroupSection(
    val group: MeterGroupEntity?,
    val items: List<MeterItem>,
) {
    val totalMinor: Long get() = items.sumOf { it.amountMinor }
}

/** Creating or renaming a group. */
data class GroupDialogState(
    /** Null when creating a new group. */
    val groupId: String?,
    val nameText: String,
) {
    val canSave: Boolean get() = nameText.isNotBlank()
}

/** One service to be paid in a batch: what it settles and for how much. */
data class BatchPayItem(
    val settlesId: String,
    val dueEpochDay: Long,
    val note: String,
    val amountMinor: Long,
)

/**
 * Paying several ticked services at once. Every item becomes its own expense settling its
 * own service, so each keeps its paid state - but it is one confirmation and one undo.
 */
data class BatchPayDialogState(
    val items: List<BatchPayItem>,
    val accountId: String,
    val categoryId: String,
    val dateEpochDay: Long,
) {
    val totalMinor: Long get() = items.sumOf { it.amountMinor }
    val canSave: Boolean get() = items.isNotEmpty() && accountId.isNotEmpty() && categoryId.isNotEmpty()
}

/** The expenses just booked, kept only long enough to offer taking them back. */
data class BatchPaidUndo(val transactionIds: List<String>)

data class UtilitiesUiState(
    val sections: List<MeterGroupSection> = emptyList(),
    /** Every group, for the "move to group" picker in the form. */
    val groups: List<MeterGroupEntity> = emptyList(),
    /** Ticked services, by meter id. */
    val selectedIds: Set<String> = emptySet(),
    val isLoaded: Boolean = false,
) {
    val isEmpty: Boolean get() = isLoaded && sections.isEmpty()

    private val payable: List<MeterItem>
        get() = sections.flatMap { it.items }.filter { it.isPayable && it.meter.id in selectedIds }

    val selectedCount: Int get() = payable.size
    val selectedTotalMinor: Long get() = payable.sumOf { it.amountMinor }
}

/**
 * Entering a reading, with what it will cost worked out as you type.
 *
 * The charge is shown before it is saved because a meter reading is easy to fat-finger and
 * hard to sanity-check: 16377 against 16180 is 197 kWh and 1 252,92 руб., and a stray digit
 * turning that into 163770 is obvious the moment the roubles appear.
 *
 * [previousMilli] is null for the meter's first reading, which bills nothing - it only says
 * where counting starts.
 */
data class ReadingDialogState(
    val meterId: String,
    val meterName: String,
    val unitRes: Int,
    val tariffMinor: Long,
    val drainageTariffMinor: Long,
    val previousMilli: Long?,
    val valueText: String,
    val dateEpochDay: Long,
) {
    val valueMilli: Long get() = parseMeterToMilli(valueText)

    val isBaseline: Boolean get() = previousMilli == null

    /** Null when the reading is below the previous one - nothing honest to show. */
    val consumedMilli: Long?
        get() = previousMilli?.let { (valueMilli - it).takeIf { consumed -> consumed >= 0 } }

    /** The supply part alone, so a water meter can show вода and водоотведение apart. */
    val supplyMinor: Long? get() = consumedMilli?.let { chargeMinor(it, tariffMinor) }

    val drainageMinor: Long?
        get() = consumedMilli?.let { if (drainageTariffMinor > 0) chargeMinor(it, drainageTariffMinor) else 0 }

    val hasDrainage: Boolean get() = drainageTariffMinor > 0

    val chargeMinor: Long?
        get() = consumedMilli?.let { combinedChargeMinor(it, tariffMinor, drainageTariffMinor) }

    val isBelowPrevious: Boolean
        get() = valueText.isNotEmpty() && previousMilli != null && valueMilli < previousMilli

    val canSave: Boolean get() = valueText.isNotEmpty() && !isBelowPrevious
}

@HiltViewModel
class UtilitiesViewModel @Inject constructor(
    private val meterRepository: MeterRepository,
    private val meterGroupRepository: MeterGroupRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _readingDialog = MutableStateFlow<ReadingDialogState?>(null)
    val readingDialog: StateFlow<ReadingDialogState?> = _readingDialog.asStateFlow()

    private val _groupDialog = MutableStateFlow<GroupDialogState?>(null)
    val groupDialog: StateFlow<GroupDialogState?> = _groupDialog.asStateFlow()

    private val _payDialog = MutableStateFlow<BatchPayDialogState?>(null)
    val payDialog: StateFlow<BatchPayDialogState?> = _payDialog.asStateFlow()

    private val _paidUndo = MutableStateFlow<BatchPaidUndo?>(null)
    val paidUndo: StateFlow<BatchPaidUndo?> = _paidUndo.asStateFlow()

    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    private var accounts: List<AccountEntity> = emptyList()
    private var utilitiesCategoryId: String? = null

    val uiState: StateFlow<UtilitiesUiState> = combine(
        meterRepository.observeAll(),
        meterRepository.observeAllReadings(),
        meterGroupRepository.observeAll(),
        transactionRepository.observePaidThrough(),
        selectedIds,
    ) { meters, readings, groups, paidThrough, selected ->
        // One query for every meter's readings, grouped here: a per-meter query would be
        // N+1 for a screen that shows them all.
        val latest = readings.groupBy { it.meterId }
        val items = meters.map { toItem(it, latest[it.id]?.firstOrNull(), paidThrough) }
        val byGroup = items.groupBy { it.meter.groupId }

        // Named groups first, in their own order; anything ungrouped falls into «Прочее» at
        // the end - and only if there is something there.
        val sections = groups.mapNotNull { group ->
            byGroup[group.id]?.let { MeterGroupSection(group, it) }
        } + byGroup[null]?.let { listOf(MeterGroupSection(null, it)) }.orEmpty()

        UtilitiesUiState(sections = sections, groups = groups, selectedIds = selected, isLoaded = true)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UtilitiesUiState(),
    )

    init {
        viewModelScope.launch { utilitiesCategoryId = categoryRepository.utilitiesCategoryId() }
        viewModelScope.launch {
            accountRepository.observeActive().collect { accounts = it }
        }
    }

    /** Fills in what a service owes this month and whether a live transaction already settles it. */
    private fun toItem(
        meter: MeterEntity,
        lastReading: MeterReadingEntity?,
        paidThrough: Map<String, Long>,
    ): MeterItem {
        val monthly = meter.monthlyChargeMinor()
        return if (monthly != null) {
            // Norm or fixed: keyed by the meter and this month, paid if the latest settled due lands in it.
            MeterItem(
                meter = meter,
                lastReading = lastReading,
                amountMinor = monthly,
                isPaid = paidThrough[meter.id]?.let { YearMonth.from(LocalDate.ofEpochDay(it)) == YearMonth.now() } == true,
                settlesId = meter.id,
                settlesDueEpochDay = LocalDate.now().withDayOfMonth(1).toEpochDay(),
            )
        } else {
            // Metered: keyed by the latest reading, paid if a transaction points at that reading.
            MeterItem(
                meter = meter,
                lastReading = lastReading,
                amountMinor = lastReading?.amountMinor ?: 0,
                isPaid = lastReading != null && paidThrough.containsKey(lastReading.id),
                settlesId = lastReading?.id,
                settlesDueEpochDay = lastReading?.dateEpochDay,
            )
        }
    }

    fun onAddReadingClick(item: MeterItem) {
        _readingDialog.value = ReadingDialogState(
            meterId = item.meter.id,
            meterName = item.meter.name,
            unitRes = item.meter.type.unitRes(),
            tariffMinor = item.meter.tariffMinor,
            drainageTariffMinor = item.meter.drainageTariffMinor,
            previousMilli = item.lastReading?.valueMilli,
            valueText = "",
            dateEpochDay = LocalDate.now().toEpochDay(),
        )
    }

    fun onReadingValueChange(text: String) = _readingDialog.update {
        it?.copy(valueText = sanitizeMeterInput(text))
    }

    fun onReadingDateChange(epochDay: Long) = _readingDialog.update {
        it?.copy(dateEpochDay = epochDay)
    }

    fun onReadingDismiss() {
        _readingDialog.value = null
    }

    fun onReadingConfirm() {
        val dialog = _readingDialog.value ?: return
        if (!dialog.canSave) return
        _readingDialog.value = null

        viewModelScope.launch {
            meterRepository.addReading(
                meterId = dialog.meterId,
                valueMilli = dialog.valueMilli,
                dateEpochDay = dialog.dateEpochDay,
            )
        }
    }

    fun onCreateGroupClick() {
        _groupDialog.value = GroupDialogState(groupId = null, nameText = "")
    }

    fun onRenameGroupClick(group: MeterGroupEntity) {
        _groupDialog.value = GroupDialogState(groupId = group.id, nameText = group.name)
    }

    fun onGroupNameChange(text: String) = _groupDialog.update { it?.copy(nameText = text) }

    fun onGroupDismiss() {
        _groupDialog.value = null
    }

    fun onGroupConfirm() {
        val dialog = _groupDialog.value ?: return
        if (!dialog.canSave) return
        _groupDialog.value = null

        val name = dialog.nameText.trim()
        viewModelScope.launch {
            if (dialog.groupId == null) meterGroupRepository.create(name)
            else meterGroupRepository.rename(dialog.groupId, name)
        }
    }

    /** The group is removed; its services fall back to «Прочее», never deleted with it. */
    fun onDeleteGroup(group: MeterGroupEntity) {
        viewModelScope.launch { meterGroupRepository.delete(group.id) }
    }

    fun onToggleSelect(meterId: String) = selectedIds.update { current ->
        if (meterId in current) current - meterId else current + meterId
    }

    /** Tick or clear every payable service in a group at once - "pay this quittance". */
    fun onToggleGroup(section: MeterGroupSection) {
        val payableIds = section.items.filter { it.isPayable }.map { it.meter.id }.toSet()
        if (payableIds.isEmpty()) return
        selectedIds.update { current ->
            if (payableIds.all { it in current }) current - payableIds else current + payableIds
        }
    }

    fun onClearSelection() {
        selectedIds.value = emptySet()
    }

    /** Opens the confirmation for the ticked services, defaulting to the first account and ЖКХ. */
    fun onPaySelectedClick() {
        val accountId = accounts.firstOrNull()?.id ?: return
        val categoryId = utilitiesCategoryId ?: return
        val items = uiState.value.sections.flatMap { it.items }
            .filter { it.isPayable && it.meter.id in selectedIds.value }
            .map {
                BatchPayItem(
                    settlesId = it.settlesId!!,
                    dueEpochDay = it.settlesDueEpochDay!!,
                    note = it.meter.name,
                    amountMinor = it.amountMinor,
                )
            }
        if (items.isEmpty()) return

        _payDialog.value = BatchPayDialogState(
            items = items,
            accountId = accountId,
            categoryId = categoryId,
            dateEpochDay = LocalDate.now().toEpochDay(),
        )
    }

    fun onPayAccountChange(accountId: String) = _payDialog.update { it?.copy(accountId = accountId) }

    fun onPayDateChange(epochDay: Long) = _payDialog.update { it?.copy(dateEpochDay = epochDay) }

    fun onPayDismiss() {
        _payDialog.value = null
    }

    /** One expense per ticked service, so each keeps its own paid state; one undo for the lot. */
    fun onPayConfirm() {
        val dialog = _payDialog.value ?: return
        if (!dialog.canSave) return
        _payDialog.value = null

        viewModelScope.launch {
            val ids = dialog.items.map { item ->
                transactionRepository.addIncomeOrExpense(
                    type = TransactionType.EXPENSE,
                    amountMinor = item.amountMinor,
                    accountId = dialog.accountId,
                    categoryId = dialog.categoryId,
                    dateEpochDay = dialog.dateEpochDay,
                    note = item.note,
                    settlesPaymentId = item.settlesId,
                    settlesDueEpochDay = item.dueEpochDay,
                )
            }
            selectedIds.value = emptySet()
            _paidUndo.value = BatchPaidUndo(ids)
        }
    }

    /** Deleting the expenses is all undo needs to be: the marks were never separate from them. */
    fun onUndoPaid() {
        val undo = _paidUndo.value ?: return
        _paidUndo.value = null
        viewModelScope.launch { undo.transactionIds.forEach { transactionRepository.softDelete(it) } }
    }

    fun onUndoDismissed() {
        _paidUndo.value = null
    }

    fun accountsForPicker(): List<AccountEntity> = accounts
}
