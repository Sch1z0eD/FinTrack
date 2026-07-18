package com.findev.fintrack.update

import android.Manifest
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.findev.fintrack.R
import com.findev.fintrack.data.UpdateRepository
import java.io.File

private const val DOWNLOAD_NOTIFICATION_TAG = "app_update_ready"
private const val DOWNLOAD_NOTIFICATION_ID = 2

/**
 * Fires when the downloaded APK has landed, and offers to install it.
 *
 * The installer cannot be launched straight from here: starting an activity from the
 * background is blocked on Android 10+, so the intent is parked in a notification and the
 * user's tap is what starts it.
 */
class UpdateDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id == -1L) return

        val manager = context.getSystemService<DownloadManager>() ?: return
        val (status, localUri) = manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            // Not one of ours: the query comes back empty for another app's download.
            if (cursor == null || !cursor.moveToFirst()) return
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            status to uri
        }

        if (status != DownloadManager.STATUS_SUCCESSFUL || localUri == null) return

        val file = localUri.toUri().path?.let(::File) ?: return
        if (!file.exists()) return

        notifyReady(context, file)
    }

    private fun notifyReady(context: Context, file: File) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val install = PendingIntent.getActivity(
            context,
            DOWNLOAD_NOTIFICATION_ID,
            installIntent(context, file),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.update_ready_title))
            .setContentText(context.getString(R.string.update_ready_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(install)
            .build()

        NotificationManagerCompat.from(context)
            .notify(DOWNLOAD_NOTIFICATION_TAG, DOWNLOAD_NOTIFICATION_ID, notification)
    }
}

/**
 * The installer runs in another process, so the APK is handed over as a content:// URI with
 * a read grant - a file:// URI would be rejected outright.
 */
fun installIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, UpdateRepository.APK_MIME_TYPE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Where [UpdateRepository.downloadApk] puts the file, so the UI can find it again. */
fun downloadedApk(context: Context, versionName: String): File? {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
    return File(dir, UpdateRepository.apkFileName(versionName)).takeIf { it.exists() }
}
