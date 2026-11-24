package com.karmakameleon.android.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Keeps the list in place when the item first on screen is removed, like a deleted comment or a hidden post (#179).
 * [items] are the list's items in order, and [key] gives each the key it has in the list.
 *
 * Without this, the list keeps the removed item's index and how far it was scrolled into it. Reaching an item's actions
 * often means scrolling into it, so the items after it would start that far up, above the screen. Instead, what now
 * follows the items above the removed one starts at the top of the list, where the removed one started.
 */
@Composable
fun <T> KeepPlaceOnRemoval(
    listState: LazyListState,
    items: List<T>,
    key: (T) -> Any,
) {
    val keys = remember(items) { items.map(key) }
    var previousKeys by remember { mutableStateOf(keys) }
    SideEffect {
        val oldKeys = previousKeys
        if (keys === oldKeys) return@SideEffect
        previousKeys = keys
        // The layout is still the one before the change, so this is the item that was first on screen.
        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@SideEffect
        indexAfterRemoval(oldKeys, keys, first.key, first.index)?.let { listState.requestScrollToItem(it) }
    }
}

/**
 * Where the list should start once [newKeys] replace [oldKeys], when the item first on screen, [firstKey] at
 * [firstIndex], was removed: just after the last item above it that's still there. The items before these, like a
 * header, are counted from [firstIndex], so they have to stay the same.
 *
 * Null, leaving the list as it is, unless items were only removed: not when the first one on screen is still there or
 * isn't one of these items, like a header, and not when new items came too or none are left. New contents, like a new
 * sort, start at the top instead (`ScrollToTopOnChange`), and comments loaded in place of a "load more" row show where
 * it was, which the list already does.
 */
internal fun indexAfterRemoval(
    oldKeys: List<Any>,
    newKeys: List<Any>,
    firstKey: Any,
    firstIndex: Int,
): Int? {
    val removedAt = oldKeys.indexOf(firstKey)
    if (removedAt < 0 || newKeys.isEmpty()) return null
    val newIndexes = newKeys.withIndex().associate { (index, key) -> key to index }
    val oldKeySet = oldKeys.toHashSet()
    if (firstKey in newIndexes || newKeys.any { it !in oldKeySet }) return null
    val itemsBefore = firstIndex - removedAt
    val lastKeptAbove = (removedAt - 1 downTo 0).firstNotNullOfOrNull { newIndexes[oldKeys[it]] } ?: -1
    return itemsBefore + lastKeptAbove + 1
}
