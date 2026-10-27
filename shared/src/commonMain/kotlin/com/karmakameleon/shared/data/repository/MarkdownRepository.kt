package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.util.markdown.parseMarkdownBlocks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Parses markdown on [parseDispatcher], off the main thread, and keeps the blocks of the last [maxEntries] texts, so
 * text shown again, like a post body hidden and shown or a comment scrolled back into view, isn't parsed again. A long
 * post takes long enough to parse that doing it while drawing a frame makes the app stutter.
 */
class MarkdownRepository(
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    // Oldest first, so the entries past maxEntries are dropped from the front.
    private val parsed = MutableStateFlow<Map<String, List<MarkdownBlock>>>(emptyMap())

    /** The blocks of [markdown] if it has been parsed, without parsing it. */
    fun cached(markdown: String): List<MarkdownBlock>? = parsed.value[markdown]

    suspend fun parse(markdown: String): List<MarkdownBlock> = cached(markdown) ?: parseAll(listOf(markdown)).getValue(markdown)

    /** Parses the texts that aren't cached yet, so they're ready by the time they're shown. */
    suspend fun parseAll(markdowns: Collection<String>): Map<String, List<MarkdownBlock>> {
        val known = parsed.value
        val new = markdowns.filterNot { it in known }.distinct()
        if (new.isEmpty()) return markdowns.associateWith { known.getValue(it) }
        val blocks = withContext(parseDispatcher) { new.associateWith { parseMarkdownBlocks(it) } }
        parsed.update { cache -> (cache + blocks).keepNewest(maxEntries) }
        return markdowns.associateWith { blocks[it] ?: known.getValue(it) }
    }

    private companion object {
        // Enough for a long thread's comments and the posts around it.
        const val DEFAULT_MAX_ENTRIES = 2000
    }
}

private fun <K, V> Map<K, V>.keepNewest(count: Int): Map<K, V> {
    if (size <= count) return this
    return entries.drop(size - count).associate { it.key to it.value }
}
