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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.ui.FinTrackProgress
import com.findev.fintrack.data.ThemeMode
import com.findev.fintrack.ui.PanelCard
import com.findev.fintrack.ui.PillSelector
import com.findev.fintrack.ui.GlassAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
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
                // Flavor-specific: the github build shows the update controls here, the rustore
                // build shows the version and nothing else.
                AboutSection(versionName = viewModel.installedVersionName)
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
        GlassAlertDialog(
            onDismissRequest = { confirmRestore = null },
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
        PillSelector(
            options = ThemeMode.entries.map { it to labels.getValue(it) },
            selected = selected,
            onSelected = onSelect,
            modifier = Modifier.fillMaxWidth(),
        )
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
internal fun android.content.Context.startActivitySafely(intent: Intent) {
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
