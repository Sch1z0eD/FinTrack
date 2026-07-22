package com.findev.fintrack.ui.screens.settings

import android.app.DownloadManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findev.fintrack.data.AvailableUpdate
import com.findev.fintrack.data.NoReleasesYetException
import com.findev.fintrack.data.SettingsRepository
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

/**
 * Drives the github flavor's update card: check GitHub, download the APK and hand it to the
 * installer. Kept out of [SettingsViewModel] so the shared settings screen carries none of it
 * and the rustore flavor compiles without an update dependency in sight.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository,
    private val apkInstaller: ApkInstaller,
) : ViewModel() {

    val autoUpdateCheck: StateFlow<Boolean> = settingsRepository.autoUpdateCheck
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private var downloadWatcher: Job? = null

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
}
