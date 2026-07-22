package com.findev.fintrack.data

/** A release published on GitHub that is newer than what is installed. */
data class AvailableUpdate(
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    /** From the release asset; used to tell a finished download from a truncated one. */
    val sizeBytes: Long,
    val notes: String,
)

/**
 * Which stream of releases a build follows. The two are separate applications with separate
 * data, so a beta can be installed and updated without touching the stable one.
 */
enum class ReleaseChannel { STABLE, BETA }

/** A parsed release tag. */
data class ReleaseTag(
    val versionName: String,
    val versionCode: Long,
    val channel: ReleaseChannel,
)

/**
 * Parses `v1.2.3` and `v1.2.3-beta`.
 *
 * Version numbers are shared across the two channels and simply keep marching upwards -
 * a beta at 1.2.4 followed by a stable at 1.2.5. Nothing has to keep two counters in step,
 * and because the channels are separate applications, a beta and a stable at the same
 * number never meet.
 */
fun parseReleaseTag(tag: String): ReleaseTag? {
    val withoutPrefix = tag.removePrefix("v")
    val isBeta = withoutPrefix.endsWith(BETA_SUFFIX)
    val numbers = if (isBeta) withoutPrefix.removeSuffix(BETA_SUFFIX) else withoutPrefix

    val code = versionCodeFromTag(numbers) ?: return null
    return ReleaseTag(
        versionName = withoutPrefix,
        versionCode = code,
        channel = if (isBeta) ReleaseChannel.BETA else ReleaseChannel.STABLE,
    )
}

private const val BETA_SUFFIX = "-beta"

/**
 * Makes GitHub's release body readable in a plain Text.
 *
 * The notes are Markdown and the update card renders them as-is, so `**bold**` arrives with
 * its asterisks showing. This strips the emphasis markers and drops the auto-generated
 * "Full Changelog" link, which is a URL no one is going to type off a phone screen.
 */
fun formatReleaseNotes(raw: String): String = raw
    .lineSequence()
    .map { it.trim() }
    .filterNot { line ->
        line.startsWith("**Full Changelog**") ||
            line.startsWith("Full Changelog") ||
            // Older releases carried this; the app reads the version from the tag instead.
            line.startsWith("versionCode:")
    }
    .map { it.replace("**", "").replace("__", "") }
    .joinToString("\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

/**
 * Turns a release tag into a comparable version code, using the same formula the release
 * workflow uses to stamp the APK. Both sides deriving it from the tag is what keeps them
 * from disagreeing - there is no second place to update.
 *
 * Returns null for anything that is not exactly `vMAJOR.MINOR.PATCH`: a tag like `nightly`
 * or `v2.0` is not a release this app knows how to compare against, and guessing would risk
 * offering a downgrade as an update.
 */
fun versionCodeFromTag(tag: String): Long? {
    val name = tag.removePrefix("v")
    val parts = name.split('.')
    if (parts.size != 3) return null

    val numbers = parts.map { part ->
        // Reject "01" and " 1" as well as "x": toIntOrNull would accept a leading plus
        // sign and surrounding text is never intentional in a tag.
        if (part.isEmpty() || !part.all { it.isDigit() }) return null
        part.toIntOrNull() ?: return null
    }
    val (major, minor, patch) = numbers
    // Monotonic while minor and patch stay below 100, which the release workflow assumes too.
    if (minor >= 100 || patch >= 100) return null

    return major * 10_000L + minor * 100L + patch
}
