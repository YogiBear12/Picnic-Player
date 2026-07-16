package app.picnic.player.ui.onboarding

/**
 * Applies a saved id ordering to [items]: ids present in [order] come
 * first in that order, then any remaining items keep their original relative
 * order. Stable and tolerant of stale/missing ids on either side.
 */
fun <T> applyPickerOrder(items: List<T>, order: List<String>, id: (T) -> String): List<T> {
    if (order.isEmpty()) return items
    val byId = items.associateBy(id)
    val ordered = order.mapNotNull { byId[it] }
    val rest = items.filterNot { id(it) in order }
    return ordered + rest
}
