package com.findev.fintrack.ui.screens.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.R
import com.findev.fintrack.data.BackupRepository
import com.findev.fintrack.data.ImportResult
import com.findev.fintrack.data.SettingsRepository
import com.findev.fintrack.data.backupFileName
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

/** What the backup card is showing. */
sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Working : BackupUiState
    data class Exported(val rows: Int) : BackupUiState
    data class Imported(val rows: Int) : BackupUiState
    data class Failed(val reason: String) : BackupUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** Read from the running package, so it is right in every flavor and build type. */
    val installedVersionName: String
        get() = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            .orEmpty()

    init {
        refresh()
    }

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    /** Name offered to the file picker, so the user does not have to invent one. */
    fun suggestedBackupName(): String = backupFileName()

    fun onExport(target: Uri) {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            _backupState.value = try {
                BackupUiState.Exported(backupRepository.export(target, installedVersionName))
            } catch (e: Exception) {
                BackupUiState.Failed(e.message ?: "не удалось сохранить")
            }
        }
    }

    fun onImport(source: Uri) {
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            _backupState.value = when (val result = backupRepository.import(source)) {
                is ImportResult.Success -> BackupUiState.Imported(result.rowsRestored)
                is ImportResult.TooNew -> BackupUiState.Failed(
                    context.getString(
                        R.string.backup_too_new,
                        result.fileSchema,
                        result.appSchema,
                    ),
                )
                is ImportResult.Invalid -> BackupUiState.Failed(result.reason)
            }
        }
    }

    fun onBackupMessageShown() {
        _backupState.value = BackupUiState.Idle
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
