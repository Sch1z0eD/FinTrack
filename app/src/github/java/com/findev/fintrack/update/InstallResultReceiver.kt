package com.findev.fintrack.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/**
 * Carries a [PackageInstaller] session through its two steps: the system first asks for the
 * user's confirmation, then reports how it went.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The system hands back an intent that shows the confirmation dialog. It has
                // to be started by us, which works because the install is kicked off from the
                // foreground - the same reason the installer can open without a notification.
                // The typed overload on Intent is API 33; minSdk here is 31.
                val confirm = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                ) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }

            // Success needs no announcement: the app is about to be replaced, and Android
            // shows its own "app installed" screen. Failures are surfaced by the installer
            // dialog the user was just looking at.
            else -> Unit
        }
    }
}
