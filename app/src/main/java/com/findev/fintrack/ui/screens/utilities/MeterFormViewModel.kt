package com.findev.fintrack.ui.screens.utilities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.MeterGroupRepository
import com.findev.fintrack.data.MeterRepository
import com.findev.fintrack.data.combinedChargeMinor
import com.findev.fintrack.data.local.entity.BillingKind
import com.findev.fintrack.data.local.entity.MeterGroupEntity
import com.findev.fintrack.data.local.entity.MeterType
import com.findev.fintrack.ui.formatAmountForInput
import com.findev.fintrack.ui.formatMeterForInput
import com.findev.fintrack.ui.navigation.METER_FORM_ARG_ID
import com.findev.fintrack.ui.parseAmountToMinor
import com.findev.fintrack.ui.parseMeterToMilli
import com.findev.fintrack.ui.sanitizeAmountInput
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
import javax.inject.Inject

data class MeterFormUiState(
    val name: String = "",
    val type: MeterType = MeterType.ELECTRICITY,
    val billing: BillingKind = BillingKind.METERED,
    /** Kept as text; kopecks are derived, like every other amount field. */
    val tariffText: String = "",
    /** Водоотведение tariff, offered only for water; charged on the same volume as [tariffText]. */
    val drainageText: String = "",
    /** Monthly volume for a normative service; parsed like a reading, not like money. */
    val normText: String = "",
    val reminderDayText: String = "",
    /** The group this service is filed under, or null for «Без группы». */
    val groupId: String? = null,
    /** Every group, for the picker. */
    val groups: List<MeterGroupEntity> = emptyList(),
    val isEditing: Boolean = false,
) {
    val tariffMinor: Long get() = parseAmountToMinor(tariffText)
    val drainageMinor: Long get() = parseAmountToMinor(drainageText)
    val normMilli: Long get() = parseMeterToMilli(normText)
    val reminderDay: Int get() = reminderDayText.toIntOrNull() ?: 0

    val isNorm: Boolean get() = billing == BillingKind.NORM
    val isFixed: Boolean get() = billing == BillingKind.FIXED
    val isMetered: Boolean get() = billing == BillingKind.METERED

    /** Only water carries a second (drainage) tariff, and only when it is not a flat fee. */
    val isWater: Boolean
        get() = !isFixed && (type == MeterType.COLD_WATER || type == MeterType.HOT_WATER)

    /** Live preview of the monthly charge for a norm or fixed service. */
    val monthlyChargeMinor: Long?
        get() = when (billing) {
            BillingKind.FIXED -> tariffMinor.takeIf { it > 0 }
            BillingKind.NORM -> if (normMilli > 0) combinedChargeMinor(normMilli, tariffMinor, drainageForType()) else null
            BillingKind.METERED -> null
        }

    /** Drainage only applies to water; anything typed on a non-water meter is ignored. */
    fun drainageForType(): Long = if (isWater) drainageMinor else 0

    val canSave: Boolean
        get() = name.isNotBlank() && tariffMinor > 0 && when (billing) {
            BillingKind.NORM -> normMilli > 0
            BillingKind.FIXED -> true
            BillingKind.METERED -> reminderDay in 1..31
        }
}

@HiltViewModel
class MeterFormViewModel @Inject constructor(
    private val meterRepository: MeterRepository,
    private val meterGroupRepository: MeterGroupRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null when creating. */
    private val editedId: String? = savedStateHandle[METER_FORM_ARG_ID]

    private val _uiState = MutableStateFlow(MeterFormUiState(isEditing = editedId != null))
    val uiState: StateFlow<MeterFormUiState> = _uiState.asStateFlow()

    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    init {
        // Merged with copy(), not by replacing state, so it and the edit-load below can land
        // in either order without one clobbering the other.
        viewModelScope.launch {
            meterGroupRepository.observeAll().collect { groups ->
                _uiState.update { it.copy(groups = groups) }
            }
        }
        editedId?.let { id ->
            viewModelScope.launch {
                val meter = meterRepository.getById(id) ?: return@launch
                _uiState.update {
                    it.copy(
                        name = meter.name,
                        type = meter.type,
                        billing = meter.billing,
                        tariffText = formatAmountForInput(meter.tariffMinor),
                        drainageText = if (meter.drainageTariffMinor > 0) formatAmountForInput(meter.drainageTariffMinor) else "",
                        normText = if (meter.normMilli > 0) formatMeterForInput(meter.normMilli) else "",
                        reminderDayText = meter.reminderDay.takeIf { d -> d > 0 }?.toString().orEmpty(),
                        groupId = meter.groupId,
                        isEditing = true,
                    )
                }
            }
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }

    fun onTypeChange(type: MeterType) = _uiState.update { it.copy(type = type) }

    fun onBillingChange(billing: BillingKind) = _uiState.update { it.copy(billing = billing) }

    fun onGroupChange(groupId: String?) = _uiState.update { it.copy(groupId = groupId) }

    fun onTariffChange(text: String) = _uiState.update { it.copy(tariffText = sanitizeAmountInput(text)) }

    fun onDrainageChange(text: String) = _uiState.update { it.copy(drainageText = sanitizeAmountInput(text)) }

    fun onNormChange(text: String) = _uiState.update { it.copy(normText = sanitizeMeterInput(text)) }

    fun onReminderDayChange(text: String) = _uiState.update {
        it.copy(reminderDayText = text.filter(Char::isDigit).take(2))
    }

    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return

        // A normative service takes no readings, so it has nothing to be reminded about and
        // no norm to keep once it gets a meter. Whichever branch is off is stored empty
        // rather than left holding a stale number from the other one.
        val normMilli = if (state.isNorm) state.normMilli else 0
        val reminderDay = if (state.isMetered) state.reminderDay else 0

        val drainageMinor = state.drainageForType()

        viewModelScope.launch {
            if (editedId == null) {
                meterRepository.create(
                    name = state.name.trim(),
                    type = state.type,
                    billing = state.billing,
                    tariffMinor = state.tariffMinor,
                    drainageTariffMinor = drainageMinor,
                    normMilli = normMilli,
                    reminderDay = reminderDay,
                    groupId = state.groupId,
                )
            } else {
                meterRepository.update(
                    id = editedId,
                    name = state.name.trim(),
                    type = state.type,
                    billing = state.billing,
                    tariffMinor = state.tariffMinor,
                    drainageTariffMinor = drainageMinor,
                    normMilli = normMilli,
                    reminderDay = reminderDay,
                    groupId = state.groupId,
                )
            }
            savedChannel.send(Unit)
        }
    }

    fun onDelete() {
        val id = editedId ?: return
        viewModelScope.launch {
            meterRepository.delete(id)
            savedChannel.send(Unit)
        }
    }
}
