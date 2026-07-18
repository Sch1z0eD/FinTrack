package com.findev.fintrack.ui.screens.settings

import android.Manifest
import android.app.AlarmManager
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.AvailableUpdate
import com.findev.fintrack.data.NoReleasesYetException
import com.findev.fintrack.data.SettingsRepository
import com.findev.fintrack.data.ThemeMode
import com.findev.fintrack.data.UpdateRepository
import com.findev.fintrack.update.ApkInstaller
import com.findev.fintrack.update.DOWNLOAD_NOTIFICATION_ID
import com.findev.fintrack.update.DOWNLOAD_NOTIFICATION_TAG
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** DownloadManager has no progress callback, so the screen asks it about twice a second. */
private const val POLL_INTERVAL_MS = 500L

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
    /** [progress] is null until the server reports a size. */
    data class Downloading(val update: AvailableUpdate, val progress: Float?) : UpdateUiState
    /**
     * [openInstaller] is true only when the download just finished under the user's eyes.
     * Finding an already-downloaded file during a check is not a reason to throw the system
     * install dialog at someone who asked what version they were on.
     */
    data class ReadyToInstall(
        val update: AvailableUpdate,
        val file: File,
        val openInstaller: Boolean,
    ) : UpdateUiState
    data class Failed(val reason: String) : UpdateUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val apkInstaller: ApkInstaller,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val autoUpdateCheck: StateFlow<Boolean> = settingsRepository.autoUpdateCheck
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private var downloadWatcher: Job? = null

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
                        // Nothing to install: anything still sitting in the download folder
                        // is a leftover of an update that has already been applied.
                        updateRepository.clearDownloads(keepVersionName = null)
                        UpdateUiState.UpToDate
                    } else {
                        // Everything except this version is junk, including the half-finished
                        // and the duplicate-named copies of it.
                        updateRepository.clearDownloads(keepVersionName = update.versionName)

                        val existing = updateRepository.downloadedApk(update)
                        if (existing != null) {
                            UpdateUiState.ReadyToInstall(update, existing, openInstaller = false)
                        } else {
                            UpdateUiState.Available(update)
                        }
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
        // Otherwise DownloadManager finds the name taken and writes a second copy beside it.
        updateRepository.clearDownloads(keepVersionName = null)

        val id = updateRepository.downloadApk(update) ?: return
        _updateState.value = UpdateUiState.Downloading(update, progress = null)

        // Watched here so the installer can open the moment the file lands, while the user
        // is still looking at the screen they started the download from. The broadcast
        // receiver stays as the fallback for when they have left the app - it can only put
        // up a notification, because an activity cannot be started from the background.
        downloadWatcher?.cancel()
        downloadWatcher = viewModelScope.launch {
            while (isActive) {
                val state = updateRepository.downloadState(id)
                when (state?.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = updateRepository.downloadedApk(update)
                        _updateState.value = if (file == null) {
                            UpdateUiState.Failed("файл не найден после загрузки")
                        } else {
                            UpdateUiState.ReadyToInstall(update, file, openInstaller = true)
                        }
                        return@launch
                    }

                    DownloadManager.STATUS_FAILED, null -> {
                        _updateState.value = UpdateUiState.Failed("загрузка не удалась")
                        return@launch
                    }

                    else -> _updateState.value =
                        UpdateUiState.Downloading(update, state.fraction)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Hands the downloaded APK to the system installer. Called automatically the moment the
     * download finishes while the screen is open, and by the button if that dialog was
     * dismissed.
     */
    fun onInstall() {
        val ready = _updateState.value as? UpdateUiState.ReadyToInstall ?: return
        NotificationManagerCompat.from(context)
            .cancel(DOWNLOAD_NOTIFICATION_TAG, DOWNLOAD_NOTIFICATION_ID)

        viewModelScope.launch {
            apkInstaller.install(ready.file).onFailure {
                _updateState.value = UpdateUiState.Failed(it.message.orEmpty())
            }
        }
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
