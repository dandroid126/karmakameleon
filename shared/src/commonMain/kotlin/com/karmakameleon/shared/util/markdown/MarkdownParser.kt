package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownBlock
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.withParsedSelfText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Parses markdown on [parseDispatcher], off the main thread: a long post takes long enough to parse that doing it while
 * drawing a frame makes the app stutter. What it parses is kept with the post or comment it belongs to, so text shown
 * again, like a post body hidden and shown or a comment scrolled back into view, isn't parsed again.
 */
class MarkdownParser(
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun parse(markdown: String): List<MarkdownBlock> = withContext(parseDispatcher) { parseMarkdownBlocks(markdown) }

    suspend fun withParsedSelfText(post: Post): Post = withContext(parseDispatcher) { post.withParsedSelfText() }
}
