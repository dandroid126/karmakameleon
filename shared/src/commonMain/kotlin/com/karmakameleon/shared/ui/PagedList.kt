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
