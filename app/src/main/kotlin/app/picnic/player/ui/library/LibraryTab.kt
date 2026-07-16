package app.picnic.player.ui.library

/** The sub-views of one library pane, shown as a top tab row. */
enum class LibraryTab(val label: String) {
    LIBRARY("Library"),
    FOR_YOU("For you"),
    GENRES("Genres"),

    /** Only offered when the server has at least one collection (box set). */
    COLLECTIONS("Collections")
}
