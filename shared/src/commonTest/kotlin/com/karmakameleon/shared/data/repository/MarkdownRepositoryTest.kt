package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.util.markdown.parseMarkdownBlocks
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class MarkdownRepositoryTest {
    @Test
    fun cached_isNullUntilTheTextIsParsed() =
        runTest {
            val repo = MarkdownRepository(StandardTestDispatcher(testScheduler))
            assertNull(repo.cached("**bold**"))

            val blocks = repo.parse("**bold**")

            assertEquals(parseMarkdownBlocks("**bold**"), blocks)
            assertSame(blocks, repo.cached("**bold**"))
        }

    @Test
    fun parse_returnsTheSameBlocksForTextParsedBefore() =
        runTest {
            val repo = MarkdownRepository(StandardTestDispatcher(testScheduler))
            val first = repo.parse("text")

            // The same objects, so Compose can skip the blocks that didn't change.
            assertSame(first, repo.parse("text"))
        }

    @Test
    fun parseAll_parsesOnlyTheNewTexts() =
        runTest {
            val repo = MarkdownRepository(StandardTestDispatcher(testScheduler))
            val known = repo.parse("known")

            val all = repo.parseAll(listOf("known", "new", "new"))

            assertEquals(setOf("known", "new"), all.keys)
            assertSame(known, all["known"])
            assertSame(all["new"], repo.cached("new"))
        }

    @Test
    fun parseAll_keepsTheNewestTexts() =
        runTest {
            val repo = MarkdownRepository(StandardTestDispatcher(testScheduler), maxEntries = 2)
            repo.parse("one")
            repo.parse("two")

            repo.parse("three")

            assertNull(repo.cached("one"))
            assertNotNull(repo.cached("two"))
            assertNotNull(repo.cached("three"))
        }
}
