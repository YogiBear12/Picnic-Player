package app.picnic.player.data.seerr

enum class FilmographyTab {
    Actor,
    Director,
    Writer,
    Producer,
    Composer,
    Appearances,
    AdditionalCredits
}

data class FilmographyRow(
    val year: String?,
    val title: String,
    val roleAs: String?,
    val inLibrary: Boolean,
    val item: SeerrCatalogItem
)

data class FilmographyTabContent(
    val tab: FilmographyTab,
    val rows: List<FilmographyRow>
)

private val TabOrder = listOf(
    FilmographyTab.Director,
    FilmographyTab.Writer,
    FilmographyTab.Producer,
    FilmographyTab.Composer,
    FilmographyTab.Actor,
    FilmographyTab.Appearances,
    FilmographyTab.AdditionalCredits
)

private val NamedTabs = TabOrder - setOf(FilmographyTab.Appearances, FilmographyTab.AdditionalCredits)

private val CrewDepartmentTabs = mapOf(
    "directing" to FilmographyTab.Director,
    "writing" to FilmographyTab.Writer,
    "production" to FilmographyTab.Producer
)

private val CanonicalJobs = mapOf(
    FilmographyTab.Director to "director",
    FilmographyTab.Writer to "writer",
    FilmographyTab.Producer to "producer",
    FilmographyTab.Composer to "composer"
)

private val SelfWord = Regex("(?i)\\b(self|herself|himself|themselves)\\b")
private val TrailingNote = Regex("\\s*\\([^)]*\\)\\s*$")
private val SelfPrefix = Regex("(?i)^(self|herself|himself|themselves)\\s*[-–—:]\\s*")
private val HostJob = Regex("(?i)^(co-?)?host$")
private val GuestJob = Regex("(?i)^(special\\s+)?guest(\\s+star)?$")
private val ComposerJobs = setOf("original music composer", "composer", "music", "songs")

private enum class AppearanceJob { Host, Guest }

fun seerrFilmography(
    credits: SeerrPersonCombinedCredits,
    knownForDepartment: String?
): List<FilmographyTabContent> {
    val tagged = buildList {
        for (entry in credits.cast) {
            val credit = entry.toPersonCredit() ?: continue
            val segments = characterSegments(entry.character)
            if (segments.isEmpty()) {
                add(FilmographyTab.Actor to credit)
                continue
            }
            for (segment in segments) {
                val tab = if (isAppearanceSegment(segment)) FilmographyTab.Appearances else FilmographyTab.Actor
                add(tab to credit.copy(creditRole = segment))
            }
        }
        for (entry in credits.crew) {
            val credit = entry.toPersonCredit() ?: continue
            add(crewTab(entry.department, entry.job) to credit)
        }
    }
    if (tagged.isEmpty()) return emptyList()

    val buckets = tagged.groupBy({ it.first }, { it.second })
        .mapValues { (_, credits) -> credits.groupBy { it.mediaType to it.tmdbId } }
    return tabOrder(firstTab(knownForDepartment, buckets)).mapNotNull { tab ->
        val groups = buckets[tab] ?: return@mapNotNull null
        val rows = groups.values
            .map { mergeCredits(it, tab) }
            .sortedByReleaseDateDesc { it.item.releaseDate }
        FilmographyTabContent(tab, rows)
    }
}

private fun isAppearanceSegment(segment: String): Boolean {
    val value = segment.trim()
    if (value.isEmpty()) return false
    return SelfWord.containsMatchIn(value) || appearanceJob(value) != null
}

private fun characterSegments(character: String?): List<String> = character.orEmpty().split('/').map { it.trim() }.filter { it.isNotEmpty() }

private fun appearanceJob(segment: String): AppearanceJob? {
    val job = SelfPrefix.replace(TrailingNote.replace(segment.trim(), "").trim(), "").trim()
    if (job.isEmpty()) return null
    return when {
        HostJob.matches(job) -> AppearanceJob.Host
        GuestJob.matches(job) -> AppearanceJob.Guest
        else -> null
    }
}

private fun knownForTab(department: String?): FilmographyTab? {
    val value = department?.trim()?.lowercase()
    return when (value) {
        "acting" -> FilmographyTab.Actor
        "sound" -> FilmographyTab.Composer
        else -> CrewDepartmentTabs[value]
    }
}

private fun crewTab(department: String?, job: String?): FilmographyTab {
    val value = department?.trim()?.lowercase()
    if (value == "sound") {
        return if (isComposerJob(job)) FilmographyTab.Composer else FilmographyTab.AdditionalCredits
    }
    return CrewDepartmentTabs[value] ?: FilmographyTab.AdditionalCredits
}

private fun isComposerJob(job: String?): Boolean {
    val value = TrailingNote.replace(job?.trim().orEmpty(), "").trim().lowercase()
    return value in ComposerJobs
}

private fun firstTab(
    knownForDepartment: String?,
    buckets: Map<FilmographyTab, Map<*, *>>
): FilmographyTab? {
    val namedPresent = NamedTabs.filter { buckets[it]?.isNotEmpty() == true }
    val known = knownForTab(knownForDepartment)
    if (known != null && known in namedPresent) return known
    return namedPresent.maxByOrNull { buckets.getValue(it).size }
}

private fun tabOrder(first: FilmographyTab?): List<FilmographyTab> = if (first == null) TabOrder else listOf(first) + TabOrder.filter { it != first }

private fun mergeCredits(credits: List<SeerrPersonCredit>, tab: FilmographyTab): FilmographyRow {
    val first = credits.first()
    val linked = credits.firstOrNull { it.mediaInfo.hasLibraryLink() }
    val newestDate = credits.minWith(releaseDateDescComparator { it.releaseDate }).releaseDate
    val primary = linked ?: first
    return FilmographyRow(
        year = filmographyYear(newestDate),
        title = first.title,
        roleAs = joinRoles(credits.mapNotNull { it.creditRole }, tab),
        inLibrary = linked != null,
        item = primary.toCatalogItem().copy(releaseDate = newestDate, title = first.title)
    )
}

private fun joinRoles(roles: List<String>, tab: FilmographyTab): String? {
    val canonical = CanonicalJobs[tab]
    val kept = roles.mapNotNull { role ->
        role.trim().takeIf { it.isNotEmpty() && !it.equals(canonical, ignoreCase = true) }
    }.distinct()
    if (tab == FilmographyTab.Appearances) return joinAppearanceRoles(kept)
    return kept.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

private fun joinAppearanceRoles(roles: List<String>): String? {
    if (roles.isEmpty()) return null
    val host = roles.firstOrNull { appearanceJob(it) == AppearanceJob.Host }
    val guest = roles.firstOrNull { appearanceJob(it) == AppearanceJob.Guest }
    if (host != null && guest != null) return "Self - Host, Guest"
    if (host != null) return host
    if (guest != null) return guest
    return roles.joinToString(", ")
}

private fun filmographyYear(releaseDate: String?): String? = releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
