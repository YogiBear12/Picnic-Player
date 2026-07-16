package app.picnic.player.ui.browse

import app.picnic.player.data.media.HomeRow

/** Stable LazyColumn key for a home browse row. */
internal fun homeRowKey(row: HomeRow): String = if (row.continueWatching) "continue-watching" else row.title
