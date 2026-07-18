package com.findev.fintrack.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.ui.FinTrackProgress
import com.findev.fintrack.data.AvailableUpdate
import com.findev.fintrack.data.RELEASES_PAGE_URL
import com.findev.fintrack.data.ThemeMode
import com.findev.fintrack.ui.FieldShape
import com.findev.fintrack.ui.PanelCard
import com.findev.fintrack.ui.dialogContainerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val autoUpdateCheck by viewModel.autoUpdateCheck.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }

    // Storage Access Framework: the user picks the file, so the app needs no storage
    // permission and the copy can land in Drive, on a memory card or anywhere else.
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let(viewModel::onExport) },
    )
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        // Asking before replacing: this is the one action here that destroys data.
        onResult = { uri -> confirmRestore = uri },
    )

    // The screen is in the foreground, which is the whole reason the system dialog can be
    // raised straight away: the confirmation the installer session asks for cannot be shown
    // from the background, and that is what the notification is for. Keyed on the file so it
    // fires once per download rather than on every recomposition.
    val justDownloaded = (updateState as? UpdateUiState.ReadyToInstall)?.takeIf { it.openInstaller }
    LaunchedEffect(justDownloaded?.file) {
        if (justDownloaded != null) viewModel.onInstall()
    }

    // Every one of these can be changed from system settings while we are backgrounded, so the
    // state is re-read each time the screen comes back to the foreground.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.refresh()
            // Denied for good (system will not show the dialog again): the only way left is the
            // system settings page, so send the user there instead of a no-op button.
            val activity = context as? Activity
            if (!granted && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            ) {
                context.startActivitySafely(appNotificationSettingsIntent(context.packageName))
            }
        },
    )

    Scaffold(
        // The app-level Scaffold already applies the status bar inset.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.quick_entry_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ThemeCard(
                    selected = themeMode,
                    onSelect = viewModel::onThemeModeChange,
                )
            }

            item {
                AboutCard(
                    versionName = viewModel.installedVersionName,
                    updateState = updateState,
                    autoCheckEnabled = autoUpdateCheck,
                    canInstall = viewModel.canInstallPackages(),
                    onCheck = viewModel::onCheckForUpdates,
                    onDownload = viewModel::onDownloadUpdate,
                    onAutoCheckChange = viewModel::onAutoUpdateCheckChange,
                    onOpenGithub = { context.startActivitySafely(Intent(Intent.ACTION_VIEW, RELEASES_PAGE_URL.toUri())) },
                    onGrantInstall = {
                        context.startActivitySafely(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                "package:${context.packageName}".toUri(),
                            ),
                        )
                    },
                    onInstall = viewModel::onInstall,
                )
            }

            item {
                BackupCard(
                    state = backupState,
                    onExport = { exportLauncher.launch(viewModel.suggestedBackupName()) },
                    onImport = { importLauncher.launch(arrayOf("application/json")) },
                )
            }

            item {
                Text(
                    text = stringResource(R.string.settings_permissions_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                PermissionCard(
                    title = stringResource(R.string.settings_notifications_title),
                    description = stringResource(R.string.settings_notifications_desc),
                    granted = state.notificationsGranted,
                    actionLabel = stringResource(R.string.settings_allow),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivitySafely(appNotificationSettingsIntent(context.packageName))
                        }
                    },
                )
            }

            item {
                PermissionCard(
                    title = stringResource(R.string.settings_exact_alarms_title),
                    description = stringResource(R.string.settings_exact_alarms_desc),
                    granted = state.exactAlarmsAllowed,
                    actionLabel = stringResource(R.string.settings_configure),
                    onAction = {
                        context.startActivitySafely(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    },
                )
            }

            item {
                PermissionCard(
                    title = stringResource(R.string.settings_battery_title),
                    description = stringResource(R.string.settings_battery_desc),
                    granted = state.batteryUnrestricted,
                    actionLabel = stringResource(R.string.settings_allow),
                    onAction = {
                        context.startActivitySafely(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                "package:${context.packageName}".toUri(),
                            ),
                        )
                    },
                )
            }
        }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = dialogContainerColor(),
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = null
                        viewModel.onImport(uri)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.backup_restore_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = null }) {
                    Text(stringResource(R.string.account_create_cancel))
                }
            },
        )
    }
}

/**
 * Backup and restore.
 *
 * Sits above the permission cards on purpose: with no server behind the app, this is the
 * only thing standing between a lost phone and a lost history, and it should not be found
 * by scrolling.
 */
@Composable
private fun BackupCard(
    state: BackupUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.backup_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.backup_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (state) {
            BackupUiState.Idle -> Unit
            BackupUiState.Working -> FinTrackProgress(modifier = Modifier.fillMaxWidth())
            is BackupUiState.Exported -> Text(
                text = stringResource(R.string.backup_exported, state.rows),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            is BackupUiState.Imported -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.backup_imported, state.rows),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.backup_restart_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is BackupUiState.Failed -> Text(
                text = stringResource(R.string.backup_failed, state.reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onExport, enabled = state !is BackupUiState.Working) {
                Text(stringResource(R.string.backup_export))
            }
            TextButton(onClick = onImport, enabled = state !is BackupUiState.Working) {
                Text(stringResource(R.string.backup_import))
            }
        }
    }
}

@Composable
private fun AboutCard(
    versionName: String,
    updateState: UpdateUiState,
    autoCheckEnabled: Boolean,
    canInstall: Boolean,
    onCheck: () -> Unit,
    onDownload: (AvailableUpdate) -> Unit,
    onAutoCheckChange: (Boolean) -> Unit,
    onOpenGithub: () -> Unit,
    onGrantInstall: () -> Unit,
    onInstall: () -> Unit,
) {
    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_about_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_version, versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (updateState) {
            UpdateUiState.Idle -> Unit

            UpdateUiState.Checking -> Text(
                text = stringResource(R.string.settings_checking),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            UpdateUiState.UpToDate -> Text(
                text = stringResource(R.string.settings_up_to_date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            UpdateUiState.NoReleases -> Text(
                text = stringResource(R.string.settings_no_releases),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is UpdateUiState.Failed -> Text(
                text = stringResource(R.string.settings_update_failed, updateState.reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            is UpdateUiState.Downloading -> {
                Text(
                    text = stringResource(R.string.settings_update_downloading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val progress = updateState.progress
                if (progress == null) {
                    FinTrackProgress(modifier = Modifier.fillMaxWidth())
                } else {
                    FinTrackProgress(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is UpdateUiState.ReadyToInstall -> {
                Text(
                    text = stringResource(
                        if (updateState.openInstaller) {
                            R.string.settings_update_ready
                        } else {
                            R.string.settings_update_downloaded
                        },
                        updateState.update.versionName,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onInstall) {
                    Text(stringResource(R.string.settings_update_install))
                }
            }

            is UpdateUiState.Available -> {
                Text(
                    text = stringResource(
                        R.string.settings_update_found,
                        updateState.update.versionName,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (updateState.update.notes.isNotBlank()) {
                    Text(
                        text = updateState.update.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Without this the installer opens and immediately refuses; better to say
                // so before the download than after it.
                if (!canInstall) {
                    Text(
                        text = stringResource(R.string.settings_install_permission),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onGrantInstall) {
                        Text(stringResource(R.string.settings_allow))
                    }
                }
                Button(onClick = { onDownload(updateState.update) }) {
                    Text(stringResource(R.string.settings_update_download))
                }
            }
        }

        // The whole row toggles, not just the switch: a 32dp target on a settings list
        // is the kind of thing you miss on a phone.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FieldShape)
                .toggleable(
                    value = autoCheckEnabled,
                    onValueChange = onAutoCheckChange,
                    role = Role.Switch,
                )
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_auto_update),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_auto_update_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoCheckEnabled,
                // Null: the row above owns the gesture, so the switch must not also claim it.
                onCheckedChange = null,
                thumbContent = if (autoCheckEnabled) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onOpenGithub) {
                Text(stringResource(R.string.settings_open_github))
            }
            TextButton(
                onClick = onCheck,
                enabled = updateState !is UpdateUiState.Checking,
            ) {
                Text(stringResource(R.string.settings_check_updates))
            }
        }
    }
}

@Composable
private fun ThemeCard(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val labels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
    )

    PanelCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.settings_theme_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.settings_theme_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ThemeMode.entries.size,
                    ),
                ) {
                    Text(labels.getValue(mode))
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    PanelCard(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            // Granting happens in a system screen, so this badge appears on return.
            AnimatedVisibility(visible = granted, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.settings_granted),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!granted) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(actionLabel)
            }
        }
    }
}

private fun appNotificationSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

/** OEM settings screens are not guaranteed to exist; falling back to app details always does. */
private fun android.content.Context.startActivitySafely(intent: Intent) {
    val launch = intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    runCatching { startActivity(launch) }.onFailure {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:$packageName".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
