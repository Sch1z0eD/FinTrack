package com.findev.fintrack.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.findev.fintrack.R
import com.findev.fintrack.ui.PanelCard

/**
 * rustore flavor: the "About" card shows the version and nothing more. RuStore forbids apps
 * that update themselves, so this build has no update check, no download-and-install path and
 * no GitHub link - the whole update flow lives only in the github source set.
 */
@Composable
fun AboutSection(versionName: String) {
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
    }
}
