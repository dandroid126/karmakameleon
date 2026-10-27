package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.util.markdown.parseMarkdownBlocks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Parses markdown on [parseDispatcher], off the main thread, and keeps the blocks of the [maxEntries] texts asked for
 * most recently, so text shown again, like a post body hidden and shown or a comment scrolled back into view, isn't
 * parsed again. A long post takes long enough to parse that doing it while drawing a frame makes the app stutter.
 */
class MarkdownRepository(
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val parsed = MutableStateFlow(ParsedTexts(emptyMap()))

    /** The blocks of [markdown] if it has been parsed, without parsing it. */
    fun cached(markdown: String): List<MarkdownBlock>? = parsed.value.blocks[markdown]

    suspend fun parse(markdown: String): List<MarkdownBlock> = parseAll(listOf(markdown)).getValue(markdown)

    /** Parses the texts that aren't cached yet, so they're ready by the time they're shown. */
    suspend fun parseAll(markdowns: Collection<String>): Map<String, List<MarkdownBlock>> {
        val known = parsed.value.blocks
        val new = markdowns.filterNot { it in known }.distinct()
        val parsedNow = if (new.isEmpty()) emptyMap() else withContext(parseDispatcher) { new.associateWith { parseMarkdownBlocks(it) } }
        val requested = markdowns.associateWith { parsedNow[it] ?: known.getValue(it) }
        // The texts asked for move to the newest end, even when they were cached, so the ones dropped are those that
        // haven't been asked for in the longest time, not those parsed first, like the body of a post still on screen.
        parsed.update { cache -> ParsedTexts((cache.blocks.filterKeys { it !in requested } + requested).keepNewest(maxEntries)) }
        return requested
    }

    private companion object {
        // Enough for a long thread's comments and the posts around it.
        const val DEFAULT_MAX_ENTRIES = 2000
    }
}

// The texts, least recently asked for first, so the ones past maxEntries are dropped from the front. Not a data class:
// a StateFlow skips a value equal to the current one, and maps are equal whatever their order, so a text moved to the
// newest end would stay where it was.
private class ParsedTexts(
    val blocks: Map<String, List<MarkdownBlock>>,
)

private fun <K, V> Map<K, V>.keepNewest(count: Int): Map<K, V> {
    if (size <= count) return this
    return entries.drop(size - count).associate { it.key to it.value }
}
