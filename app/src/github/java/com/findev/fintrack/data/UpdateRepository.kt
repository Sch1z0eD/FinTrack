package com.findev.fintrack.data

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import com.findev.fintrack.BuildConfig
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/Sch1z0eD/FinTrack/releases/latest"

private const val RELEASES_URL =
    "https://api.github.com/repos/Sch1z0eD/FinTrack/releases?per_page=20"

const val RELEASES_PAGE_URL = "https://github.com/Sch1z0eD/FinTrack/releases"

/** Release assets are named fintrack-1.2.3.apk and fintrack-beta-1.2.3.apk. */
private const val BETA_ASSET_MARKER = "-beta-"

/** The repository exists but has never published a release. */
class NoReleasesYetException : Exception("No releases published yet")

/** Progress of a queued APK download. [DownloadManager.COLUMN_STATUS] values. */
data class DownloadState(
    val status: Int,
    val bytesSoFar: Long,
    val totalBytes: Long,
) {
    /** 0..1, or null while the server has not said how big the file is. */
    val fraction: Float?
        get() = if (totalBytes > 0) (bytesSoFar.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

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
    /** Fixed at build time: a beta build never looks at the stable stream, or vice versa. */
    private val channel: ReleaseChannel =
        runCatching { ReleaseChannel.valueOf(BuildConfig.RELEASE_CHANNEL) }
            .getOrDefault(ReleaseChannel.STABLE)

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
            val release = latestReleaseForChannel() ?: throw NoReleasesYetException()

            val tag = release.getString("tag_name")
            val parsed = parseReleaseTag(tag)
                ?: error("Release tag $tag is not vMAJOR.MINOR.PATCH")

            if (parsed.versionCode <= installedVersionCode) return@runCatching null

            val asset = apkAsset(release, tag)
            AvailableUpdate(
                versionName = parsed.versionName,
                versionCode = parsed.versionCode,
                apkUrl = asset.getString("browser_download_url"),
                sizeBytes = asset.getLong("size"),
                notes = formatReleaseNotes(release.optString("body")),
            )
        }
    }

    /**
     * Stable reads /releases/latest, which GitHub already defines as "newest non-prerelease" -
     * so a beta can never be offered to a stable install by accident. Beta has to walk the
     * list itself, because that endpoint would hide exactly what it is looking for.
     */
    private fun latestReleaseForChannel(): JSONObject? = when (channel) {
        ReleaseChannel.STABLE -> JSONObject(get(LATEST_RELEASE_URL))

        ReleaseChannel.BETA -> {
            val releases = JSONArray(get(RELEASES_URL))
            (0 until releases.length())
                .map { releases.getJSONObject(it) }
                .firstOrNull { release ->
                    parseReleaseTag(release.getString("tag_name"))?.channel == ReleaseChannel.BETA
                }
        }
    }

    /** Both APKs may hang off one release, so the name decides which one belongs to us. */
    private fun apkAsset(release: JSONObject, tag: String): JSONObject {
        val assets = release.getJSONArray("assets")
        val apks = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .filter { it.getString("name").endsWith(".apk", ignoreCase = true) }

        val wantsBeta = channel == ReleaseChannel.BETA
        return apks
            .firstOrNull { it.getString("name").contains(BETA_ASSET_MARKER) == wantsBeta }
            ?: error("Release $tag has no APK for the $channel channel")
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

    /**
     * Where a queued download has got to. Polled rather than observed because DownloadManager
     * only reports through a broadcast on completion - and completion is precisely what the
     * screen wants to react to before the broadcast turns it into a notification.
     */
    fun downloadState(id: Long): DownloadState? {
        val manager = context.getSystemService<DownloadManager>() ?: return null
        return manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return null
            DownloadState(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                bytesSoFar = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                ),
                totalBytes = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                ),
            )
        }
    }

    /**
     * The already-downloaded APK for [update], or null if it is missing or incomplete.
     *
     * Size is checked rather than mere existence: DownloadManager writes straight to the
     * destination, so an interrupted download leaves a short file sitting there looking
     * exactly like a finished one.
     */
    fun downloadedApk(update: AvailableUpdate): File? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val file = File(dir, apkFileName(update.versionName))
        return file.takeIf { it.isFile && it.length() == update.sizeBytes }
    }

    /**
     * Throws away downloaded APKs, keeping only the one for [keepVersionName].
     *
     * Both halves matter. Removing the DownloadManager record clears its entry and its
     * notification; deleting stray files catches what DownloadManager itself created when
     * a repeat download found the name taken and wrote fintrack-1.2.3-1.apk beside it.
     */
    fun clearDownloads(keepVersionName: String?) {
        val keepName = keepVersionName?.let(::apkFileName)
        val manager = context.getSystemService<DownloadManager>()

        manager?.query(DownloadManager.Query())?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val uriColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
            val doomed = mutableListOf<Long>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(uriColumn)?.substringAfterLast('/')
                if (name == null || name != keepName) doomed += cursor.getLong(idColumn)
            }
            doomed.forEach { manager.remove(it) }
        }

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        dir.listFiles()
            ?.filter { it.name.endsWith(".apk", ignoreCase = true) && it.name != keepName }
            ?.forEach { it.delete() }
    }

    /** Whether the user has already allowed this app to install packages. */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        fun apkFileName(versionName: String): String = "fintrack-$versionName.apk"
    }
}
