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
import com.findev.fintrack.data.AvailableUpdate
import com.findev.fintrack.data.NoReleasesYetException
import com.findev.fintrack.data.SettingsRepository
import com.findev.fintrack.data.ThemeMode
import com.findev.fintrack.data.UpdateRepository
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

/** Where the update check has got to. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data object NoReleases : UpdateUiState
    data class Available(val update: AvailableUpdate) : UpdateUiState
    data class Downloading(val update: AvailableUpdate) : UpdateUiState
    data class Failed(val reason: String) : UpdateUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val autoUpdateCheck: StateFlow<Boolean> = settingsRepository.autoUpdateCheck
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    val installedVersionName: String get() = updateRepository.installedVersionName

    init {
        refresh()
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun onAutoUpdateCheckChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoUpdateCheck(enabled) }
    }

    fun onCheckForUpdates() {
        if (_updateState.value is UpdateUiState.Checking) return
        _updateState.value = UpdateUiState.Checking

        viewModelScope.launch {
            _updateState.value = updateRepository.fetchLatest().fold(
                onSuccess = { update ->
                    if (update == null) {
                        UpdateUiState.UpToDate
                    } else {
                        UpdateUiState.Available(update)
                    }
                },
                onFailure = { error ->
                    if (error is NoReleasesYetException) {
                        UpdateUiState.NoReleases
                    } else {
                        UpdateUiState.Failed(error.message.orEmpty())
                    }
                },
            )
        }
    }

    fun onDownloadUpdate(update: AvailableUpdate) {
        // The system downloader takes it from here and reports through its own notification;
        // UpdateDownloadReceiver posts the "install" one when the file lands.
        updateRepository.downloadApk(update)
        _updateState.value = UpdateUiState.Downloading(update)
    }

    fun canInstallPackages(): Boolean = updateRepository.canInstallPackages()

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
