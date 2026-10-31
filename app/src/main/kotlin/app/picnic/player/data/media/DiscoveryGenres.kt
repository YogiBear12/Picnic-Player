package app.picnic.player.data.media

import org.jellyfin.sdk.model.api.BaseItemKind

internal enum class DiscoveryType { MOVIE, TV, MIXED }

internal data class DiscoveryGenreGroup(
    val name: String,
    val genres: List<String>,
    val movieTitle: String?,
    val tvTitle: String?,
    val mixedTitle: String?
) {
    fun title(type: DiscoveryType): String? = when (type) {
        DiscoveryType.MOVIE -> movieTitle
        DiscoveryType.TV -> tvTitle
        DiscoveryType.MIXED -> mixedTitle
    }
}

internal fun genreGroups(available: Map<BaseItemKind, Set<String>>): Map<DiscoveryType, List<DiscoveryGenreGroup>> {
    val movies = available[BaseItemKind.MOVIE].orEmpty().map { it.lowercase() }.toSet()
    val tv = available[BaseItemKind.SERIES].orEmpty().map { it.lowercase() }.toSet()
    val known = DISCOVERY_GENRE_GROUPS.flatMap { it.genres }.map { it.lowercase() }.toSet()
    val unknown = (available[BaseItemKind.MOVIE].orEmpty() + available[BaseItemKind.SERIES].orEmpty())
        .distinctBy { it.lowercase() }
        .filter { it.lowercase() !in known && !it.equals("TV Movie", ignoreCase = true) }
        .map { DiscoveryGenreGroup(it, listOf(it), "$it movies", "$it TV shows", null) }
    val groups = DISCOVERY_GENRE_GROUPS + unknown
    val movieGroups = groups.filter { group ->
        group.title(DiscoveryType.MOVIE) != null && group.genres.any { it.lowercase() in movies }
    }
    val tvGroups = groups.filter { group ->
        group.title(DiscoveryType.TV) != null && group.genres.any { it.lowercase() in tv }
    }
    return mapOf(
        DiscoveryType.MOVIE to movieGroups,
        DiscoveryType.TV to tvGroups,
        DiscoveryType.MIXED to movieGroups.filter { it in tvGroups && it.title(DiscoveryType.MIXED) != null }
    )
}

private val DISCOVERY_GENRE_GROUPS = listOf(
    DiscoveryGenreGroup(
        "Action and adventure",
        listOf("Action", "Adventure", "Action & Adventure"),
        "Action and adventure movies",
        "Action and adventure TV shows",
        "Action and adventure"
    ),
    DiscoveryGenreGroup(
        "Sci-fi and fantasy",
        listOf("Science Fiction", "Fantasy", "Sci-Fi & Fantasy"),
        "Sci-fi and fantasy movies",
        "Sci-fi and fantasy TV shows",
        "Sci-fi and fantasy"
    ),
    DiscoveryGenreGroup(
        "Kids and family",
        listOf("Kids", "Family"),
        "Kids and family movies",
        "Kids and family TV shows",
        "Kids and family"
    ),
    DiscoveryGenreGroup(
        "Horror and thriller",
        listOf("Horror", "Thriller", "Suspense"),
        "Horror and thriller movies",
        "Horror and thriller TV shows",
        "Horror and thrillers"
    ),
    DiscoveryGenreGroup(
        "War and history",
        listOf("War", "History", "War & Politics"),
        "War and history movies",
        "War and politics TV shows",
        "War and history"
    ),
    DiscoveryGenreGroup("News and talk", listOf("News", "Talk", "Talk Show", "Talk-Show"), null, "News and talk shows", null),
    DiscoveryGenreGroup("Game shows", listOf("Game Show", "Game-Show"), null, "Game shows", null),
    DiscoveryGenreGroup(
        "Soap operas and romance",
        listOf("Soap", "Romance"),
        "Romance movies",
        "Soap operas and romance TV shows",
        "Romance"
    ),
    DiscoveryGenreGroup("Animation", listOf("Animation"), "Animated movies", "Animated TV shows", "Animation"),
    DiscoveryGenreGroup("Documentary", listOf("Documentary"), "Documentaries", "Documentary TV shows", "Documentaries"),
    DiscoveryGenreGroup("Comedy", listOf("Comedy"), "Comedy movies", "Comedy TV shows", "Comedies"),
    DiscoveryGenreGroup("Drama", listOf("Drama"), "Drama movies", "Drama TV shows", "Dramas"),
    DiscoveryGenreGroup("Mystery", listOf("Mystery"), "Mystery movies", "Mystery TV shows", "Mysteries"),
    DiscoveryGenreGroup("Western", listOf("Western"), "Western movies", "Western TV shows", "Westerns"),
    DiscoveryGenreGroup("Reality", listOf("Reality"), null, "Reality TV shows", null),
    DiscoveryGenreGroup("Miniseries", listOf("Mini-Series", "Miniseries", "Mini Series"), null, "Miniseries", null)
)
