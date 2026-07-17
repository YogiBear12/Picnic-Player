package app.picnic.player.data.update

/**
 * Semver triple parsed from a release tag or versionName. Suffixes after the
 * patch number (`-debug`, `-rc1`) are ignored for comparison.
 */
data class UpdateVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<UpdateVersion> {

    override fun compareTo(other: UpdateVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /** Parses "v1.2.3", "1.2.3", "1.2.3-debug"; null when not a semver triple. */
        fun parse(raw: String?): UpdateVersion? {
            val parts = raw
                ?.trim()
                ?.removePrefix("v")
                ?.split(".")
                ?.takeIf { it.size >= 3 }
                ?.take(3)
                ?.map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: return null }
                ?: return null
            return UpdateVersion(parts[0], parts[1], parts[2])
        }
    }
}

/** One published release, reduced to what the updater needs. */
data class UpdateRelease(
    val version: UpdateVersion,
    /** Direct download URL of the chosen APK asset. */
    val apkUrl: String,
    /** Asset size in bytes; 0 when the API did not report one. Guards the
     *  download cache — a cached APK is only reused when its size matches,
     *  so re-uploading an asset under the same tag is picked up. */
    val apkSizeBytes: Long,
    /** Release body (markdown) shown as release notes; may be blank. */
    val notes: String
)
