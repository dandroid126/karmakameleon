package com.karmakameleon.shared.util.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlainTextTest {
    // r/pics' contest post showed its asterisks in the preview (#87).
    @Test
    fun emphasisLosesItsMarkers() {
        assertEquals(
            "It's a Halloween Photo Contest!\nPost your best costume.",
            markdownPreviewText("**It's a Halloween Photo Contest!**\n\nPost your *best* costume."),
        )
        assertEquals("bold italic struck sup code", markdownPreviewText("***bold italic*** ~~struck~~ ^(sup) `code`"))
    }

    @Test
    fun headingsAndListsLoseTheirMarkers() {
        assertEquals(
            "Rules\nBe kind\nNo spam\nFirst\nSecond",
            markdownPreviewText("# Rules\n\n- Be kind\n* No spam\n\n1. First\n2. Second"),
        )
    }

    @Test
    fun linksKeepTheirTextAndLoseTheirUrls() {
        assertEquals("See the wiki or r/kotlin.", markdownPreviewText("See [the wiki](https://example.com/wiki) or r/kotlin."))
        assertEquals("See the wiki.", markdownPreviewText("See [the wiki][1].\n\n[1]: https://example.com/wiki"))
    }

    @Test
    fun spoilersStayHidden() {
        val preview = markdownPreviewText("The killer is >!the butler!<.")
        assertEquals("The killer is $HIDDEN_SPOILER.", preview)
        assertTrue("butler" !in preview)
    }

    @Test
    fun quotesCodeAndTablesKeepTheirText() {
        assertEquals("quoted", markdownPreviewText("> quoted"))
        assertEquals("val x = 1", markdownPreviewText("```kotlin\nval x = 1\n```"))
        assertEquals("A  B\n1  2", markdownPreviewText("| A | B |\n|---|---|\n| 1 | 2 |"))
    }

    @Test
    fun rulesImagesAndBlankLinesAreLeftOut() {
        assertEquals("Above\nBelow", markdownPreviewText("Above\n\n---\n\n\n\nBelow"))
        assertEquals("Look:", markdownPreviewText("Look: https://preview.redd.it/abc.png?width=640&format=png"))
    }

    @Test
    fun entitiesShowAsTheirCharacters() {
        assertEquals("Tom & Jerry", markdownPreviewText("Tom &amp; Jerry"))
    }

    // Only the start is parsed, which is all a card shows, so a long post doesn't make the feed stutter.
    @Test
    fun onlyTheStartIsKept() {
        val preview = markdownPreviewText("word ".repeat(1000))
        assertTrue(preview.length <= 1000)
        assertTrue(preview.startsWith("word word"))
    }
}
