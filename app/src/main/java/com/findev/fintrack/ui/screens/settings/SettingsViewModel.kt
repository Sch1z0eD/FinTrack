package com.findev.fintrack.ui.screens.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.SettingsRepository
import com.findev.fintrack.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The permission and system toggles the app's reminders depend on. All three are read live
 * from the OS on [refresh]; nothing here is stored, because the OS is the source of truth and
 * the user can change any of them from system settings behind the app's back.
 */
data class SettingsUiState(
    val notificationsGranted: Boolean = true,
    val exactAlarmsAllowed: Boolean = true,
    val batteryUnrestricted: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    init {
        refresh()
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    /** Called on resume: the user may have flipped any of these in system settings. */
    fun refresh() {
        _uiState.value = SettingsUiState(
            notificationsGranted = notificationsGranted(),
            exactAlarmsAllowed = exactAlarmsAllowed(),
            batteryUnrestricted = batteryUnrestricted(),
        )
    }

    private fun notificationsGranted(): Boolean {
        // Runtime notification permission only exists on Android 13+; below that it is implicit.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun exactAlarmsAllowed(): Boolean =
        context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true

    private fun batteryUnrestricted(): Boolean {
        val power = context.getSystemService<PowerManager>() ?: return true
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }
}
