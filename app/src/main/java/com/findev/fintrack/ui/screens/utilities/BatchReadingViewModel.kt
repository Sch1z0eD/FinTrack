package com.findev.fintrack.ui.screens.utilities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.MeterGroupRepository
import com.findev.fintrack.data.MeterRepository
import com.findev.fintrack.data.chargeMinor
import com.findev.fintrack.data.combinedChargeMinor
import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.ui.navigation.BATCH_READING_ARG_GROUP_ID
import com.findev.fintrack.ui.parseMeterToMilli
import com.findev.fintrack.ui.sanitizeMeterInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * One metered service's line in the batch form. Mirrors [ReadingDialogState]'s arithmetic so
 * a row shows the same consumption, water split, and charge as the single-reading dialog.
 */
data class BatchReadingRow(
    val meterId: String,
    val meterName: String,
    val unitRes: Int,
    val tariffMinor: Long,
    val drainageTariffMinor: Long,
    val previousMilli: Long?,
    val valueText: String,
) {
    val hasInput: Boolean get() = valueText.isNotEmpty()
    val valueMilli: Long get() = parseMeterToMilli(valueText)
    val isBaseline: Boolean get() = previousMilli == null

    val consumedMilli: Long?
        get() = previousMilli?.let { (valueMilli - it).takeIf { c -> c >= 0 } }

    val supplyMinor: Long? get() = consumedMilli?.let { chargeMinor(it, tariffMinor) }
    val drainageMinor: Long?
        get() = consumedMilli?.let { if (drainageTariffMinor > 0) chargeMinor(it, drainageTariffMinor) else 0 }
    val hasDrainage: Boolean get() = drainageTariffMinor > 0

    val chargeMinor: Long?
        get() = consumedMilli?.let { combinedChargeMinor(it, tariffMinor, drainageTariffMinor) }

    val isBelowPrevious: Boolean
        get() = hasInput && previousMilli != null && valueMilli < previousMilli

    /** An empty row is fine - it is simply skipped; a filled one must not be below previous. */
    val isValid: Boolean get() = !hasInput || !isBelowPrevious
}

data class BatchReadingUiState(
    val groupName: String = "",
    val rows: List<BatchReadingRow> = emptyList(),
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val isLoaded: Boolean = false,
) {
    /** Only rows that were actually filled in count towards the total. */
    val totalMinor: Long get() = rows.filter { it.hasInput }.sumOf { it.chargeMinor ?: 0 }

    val canSave: Boolean get() = rows.any { it.hasInput } && rows.all { it.isValid }
}

@HiltViewModel
class BatchReadingViewModel @Inject constructor(
    private val meterRepository: MeterRepository,
    private val meterGroupRepository: MeterGroupRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val groupId: String = requireNotNull(savedStateHandle[BATCH_READING_ARG_GROUP_ID])

    private val _uiState = MutableStateFlow(BatchReadingUiState())
    val uiState: StateFlow<BatchReadingUiState> = _uiState.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val group = meterGroupRepository.getById(groupId)
            val metered = meterRepository.getAll()
                .filter { it.groupId == groupId && it.billing == BillingKind.METERED }
            val rows = metered.map { meter ->
                val previous = meterRepository.getLatestReading(meter.id)
                BatchReadingRow(
                    meterId = meter.id,
                    meterName = meter.name,
                    unitRes = meter.type.unitRes(),
                    tariffMinor = meter.tariffMinor,
                    drainageTariffMinor = meter.drainageTariffMinor,
                    previousMilli = previous?.valueMilli,
                    valueText = "",
                )
            }
            _uiState.update { it.copy(groupName = group?.name.orEmpty(), rows = rows, isLoaded = true) }
        }
    }

    fun onValueChange(meterId: String, text: String) = _uiState.update { state ->
        state.copy(
            rows = state.rows.map { row ->
                if (row.meterId == meterId) row.copy(valueText = sanitizeMeterInput(text)) else row
            },
        )
    }

    fun onDateChange(epochDay: Long) = _uiState.update { it.copy(dateEpochDay = epochDay) }

    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            state.rows.filter { it.hasInput && it.isValid }.forEach { row ->
                meterRepository.addReading(
                    meterId = row.meterId,
                    valueMilli = row.valueMilli,
                    dateEpochDay = state.dateEpochDay,
                )
            }
            savedChannel.send(Unit)
        }
    }
}
