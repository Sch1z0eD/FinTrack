package com.findev.fintrack.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findev.fintrack.R
import com.findev.fintrack.data.AvailableUpdate
import com.findev.fintrack.data.RELEASES_PAGE_URL
import com.findev.fintrack.ui.FieldShape
import com.findev.fintrack.ui.FinTrackProgress
import com.findev.fintrack.ui.PanelCard

/**
 * github flavor: the "About" card with the full GitHub update flow - check, download, install,
 * a daily-check toggle and a link to the releases page. The rustore flavor ships a version-only
 * card under the same name (see the rustore source set).
 */
@Composable
fun AboutSection(versionName: String) {
    val viewModel: UpdateViewModel = hiltViewModel()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val autoCheckEnabled by viewModel.autoUpdateCheck.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The screen is in the foreground, which is the whole reason the system dialog can be
    // raised straight away: the confirmation the installer session asks for cannot be shown
    // from the background, and that is what the notification is for. Keyed on the file so it
    // fires once per download rather than on every recomposition.
    val justDownloaded = (updateState as? UpdateUiState.ReadyToInstall)?.takeIf { it.openInstaller }
    LaunchedEffect(justDownloaded?.file) {
        if (justDownloaded != null) viewModel.onInstall()
    }

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
                text = stringResource(R.string.settings_update_failed, (updateState as UpdateUiState.Failed).reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            is UpdateUiState.Downloading -> {
                Text(
                    text = stringResource(R.string.settings_update_downloading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val progress = (updateState as UpdateUiState.Downloading).progress
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
                val ready = updateState as UpdateUiState.ReadyToInstall
                Text(
                    text = stringResource(
                        if (ready.openInstaller) {
                            R.string.settings_update_ready
                        } else {
                            R.string.settings_update_downloaded
                        },
                        ready.update.versionName,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = viewModel::onInstall) {
                    Text(stringResource(R.string.settings_update_install))
                }
            }

            is UpdateUiState.Available -> {
                val available = updateState as UpdateUiState.Available
                Text(
                    text = stringResource(
                        R.string.settings_update_found,
                        available.update.versionName,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (available.update.notes.isNotBlank()) {
                    Text(
                        text = available.update.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Without this the installer opens and immediately refuses; better to say
                // so before the download than after it.
                if (!viewModel.canInstallPackages()) {
                    Text(
                        text = stringResource(R.string.settings_install_permission),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = {
                            context.startActivitySafely(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    "package:${context.packageName}".toUri(),
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.settings_allow))
                    }
                }
                Button(onClick = { viewModel.onDownloadUpdate(available.update) }) {
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
                    onValueChange = viewModel::onAutoUpdateCheckChange,
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
            TextButton(
                onClick = {
                    context.startActivitySafely(Intent(Intent.ACTION_VIEW, RELEASES_PAGE_URL.toUri()))
                },
            ) {
                Text(stringResource(R.string.settings_open_github))
            }
            TextButton(
                onClick = viewModel::onCheckForUpdates,
                enabled = updateState !is UpdateUiState.Checking,
            ) {
                Text(stringResource(R.string.settings_check_updates))
            }
        }
    }
}
