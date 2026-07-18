package com.findev.fintrack.data

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/Sch1z0eD/FinTrack/releases/latest"

const val RELEASES_PAGE_URL = "https://github.com/Sch1z0eD/FinTrack/releases"

/** The repository exists but has never published a release. */
class NoReleasesYetException : Exception("No releases published yet")

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000

/**
 * Checks GitHub for a newer release and hands the APK to the system downloader.
 *
 * Deliberately built on HttpURLConnection and org.json, both of which ship with Android: an
 * HTTP client and a JSON library would be two dependencies bought for one request.
 *
 * This is the only outbound network call the app makes, and it only happens when asked - by
 * the user tapping "check", or by the daily worker if they turned that on.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Version code of the APK currently running. */
    val installedVersionCode: Long
        get() = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .longVersionCode

    val installedVersionName: String
        get() = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            .orEmpty()

    /**
     * Success with null means "already up to date". Failure carries the reason so the screen
     * can say what went wrong instead of silently showing nothing.
     */
    suspend fun fetchLatest(): Result<AvailableUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(LATEST_RELEASE_URL)
            val json = JSONObject(body)

            val tag = json.getString("tag_name")
            val versionCode = versionCodeFromTag(tag)
                ?: error("Release tag \"$tag\" is not vMAJOR.MINOR.PATCH")

            if (versionCode <= installedVersionCode) return@runCatching null

            val assets = json.getJSONArray("assets")
            val apkUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk", ignoreCase = true) }
                ?.getString("browser_download_url")
                ?: error("Release $tag has no APK attached")

            AvailableUpdate(
                versionName = tag.removePrefix("v"),
                versionCode = versionCode,
                apkUrl = apkUrl,
                notes = json.optString("body").trim(),
            )
        }
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub rejects requests without one.
            setRequestProperty("User-Agent", "FinTrack")
        }
        try {
            val code = connection.responseCode
            // 404 on this endpoint means the repository has no published release yet, which
            // is a normal state and not a failure worth showing as one.
            if (code == HttpURLConnection.HTTP_NOT_FOUND) throw NoReleasesYetException()
            if (code != HttpURLConnection.HTTP_OK) {
                // 403 here is almost always the 60-per-hour unauthenticated rate limit.
                error("GitHub responded $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Queues the APK with the system downloader, which survives the app being closed and
     * shows its own progress. Lands in the app's own external files directory, so no storage
     * permission is involved and the file goes away when the app is uninstalled.
     */
    fun downloadApk(update: AvailableUpdate): Long? {
        val manager = context.getSystemService<DownloadManager>() ?: return null
        val request = DownloadManager.Request(update.apkUrl.toUri())
            .setTitle("FinTrack ${update.versionName}")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                apkFileName(update.versionName),
            )
        return manager.enqueue(request)
    }

    /** Whether the user has already allowed this app to install packages. */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        fun apkFileName(versionName: String): String = "fintrack-$versionName.apk"
    }
}
