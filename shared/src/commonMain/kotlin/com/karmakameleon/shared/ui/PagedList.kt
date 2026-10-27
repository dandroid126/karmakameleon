package com.karmakameleon.shared.ui

/** A list loaded a page at a time. [after] is Reddit's cursor for the next page. */
data class PagedList<T>(
    val items: List<T> = emptyList(),
    val after: String? = null,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
) {
    val canLoadMore: Boolean get() = !isLoadingMore && hasMore && after != null
}

/**
 * Adds the items of the next page that aren't in the list yet, telling them apart by [id]. Items move between pages as
 * their ranking changes, so the next page can repeat one. Kept, it would show twice and give the LazyColumn two items
 * with the same key, which crashes.
 */
internal fun <T> List<T>.plusNew(
    nextPage: List<T>,
    id: (T) -> String,
): List<T> {
    val ids = mapTo(HashSet(), id)
    return this + nextPage.filter { ids.add(id(it)) }
}
