package com.findev.fintrack.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs a downloaded APK through [PackageInstaller].
 *
 * The obvious route - ACTION_VIEW on the file - goes through the generic "open with" chooser,
 * because any app that claims the APK mime type is offered alongside the package installer.
 * A session hands the bytes straight to the system, which then shows its own confirmation and
 * nothing else.
 */
@Singleton
class ApkInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun install(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            )
            val sessionId = installer.createSession(params)

            installer.openSession(sessionId).use { session ->
                session.openWrite(APK_ENTRY, 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }

                // The system reports back through this: first to ask the user, then with the
                // outcome. FLAG_MUTABLE is required - the system fills in the status extras.
                val callback = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }
        }
    }

    private companion object {
        const val APK_ENTRY = "fintrack"
    }
}
