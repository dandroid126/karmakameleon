package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.util.markdown.parseMarkdownBlocks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Parses markdown on [parseDispatcher], off the main thread, and keeps the blocks of the [maxEntries] texts asked for
 * most recently, so text shown again, like a post body hidden and shown or a comment scrolled back into view, isn't
 * parsed again. A long post takes long enough to parse that doing it while drawing a frame makes the app stutter.
 *
 * Use it from the main thread, as the screens and view models do. Only the parsing runs elsewhere; the cache is read
 * and changed on the calling thread, so it needs no lock, and a text that's already parsed costs no more than a lookup
 * when it comes on screen.
 */
class MarkdownRepository(
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    // Least recently asked for first, so the entries past maxEntries are dropped from the front.
    private val parsed = LinkedHashMap<String, List<MarkdownBlock>>()

    /** The blocks of [markdown] if it has been parsed, without parsing it. */
    fun cached(markdown: String): List<MarkdownBlock>? = parsed[markdown]

    suspend fun parse(markdown: String): List<MarkdownBlock> = parseAll(listOf(markdown)).getValue(markdown)

    /** Parses the texts that aren't cached yet, so they're ready by the time they're shown. */
    suspend fun parseAll(markdowns: Collection<String>): Map<String, List<MarkdownBlock>> {
        // Taken before parsing, since another request can drop them while this one waits.
        val known = markdowns.mapNotNull { text -> parsed[text]?.let { text to it } }.toMap()
        val new = markdowns.filterNot { it in known }.distinct()
        val parsedNow = if (new.isEmpty()) emptyMap() else withContext(parseDispatcher) { new.associateWith { parseMarkdownBlocks(it) } }
        val requested = markdowns.associateWith { parsedNow[it] ?: known.getValue(it) }
        // The texts asked for move to the newest end, even when they were cached, so the ones dropped are those that
        // haven't been asked for in the longest time, not those parsed first, like the body of a post still on screen.
        for ((text, blocks) in requested) {
            parsed.remove(text)
            parsed[text] = blocks
        }
        while (parsed.size > maxEntries) parsed.remove(parsed.keys.first())
        return requested
    }

    private companion object {
        // Enough for a long thread's comments and the posts around it.
        const val DEFAULT_MAX_ENTRIES = 2000
    }
}
