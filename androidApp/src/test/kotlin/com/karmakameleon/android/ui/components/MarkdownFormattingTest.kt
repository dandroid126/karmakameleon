package com.karmakameleon.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownFormattingTest {
    // The text with [ and ] around the selection, or | at the cursor, to read the expected results at a glance.
    private fun TextWithSelection.marked(): String {
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        if (start == end) return text.substring(0, start) + "|" + text.substring(start)
        return text.substring(0, start) + "[" + text.substring(start, end) + "]" + text.substring(end)
    }

    // Reads text marked like marked() writes it.
    private fun selection(marked: String): TextWithSelection {
        val cursor = marked.indexOf('|')
        if (cursor >= 0) return TextWithSelection(marked.removeRange(cursor, cursor + 1), cursor)
        val start = marked.indexOf('[')
        val end = marked.indexOf(']') - 1
        return TextWithSelection(marked.replace("[", "").replace("]", ""), start, end)
    }

    private fun toggle(
        marked: String,
        markers: MarkdownMarkers,
    ) = selection(marked).toggleMarkers(markers).marked()

    @Test
    fun cursor_insertsAnEmptyPairAroundIt() {
        assertEquals("Hello *|*", toggle("Hello |", MarkdownMarkers.Italic))
        assertEquals("**|**Hello", toggle("|Hello", MarkdownMarkers.Bold))
        assertEquals("a >!|!< b", toggle("a | b", MarkdownMarkers.Spoiler))
    }

    @Test
    fun cursorInAnEmptyPair_removesIt() {
        assertEquals("Hello |", toggle("Hello *|*", MarkdownMarkers.Italic))
        assertEquals("a | b", toggle("a >!|!< b", MarkdownMarkers.Spoiler))
    }

    @Test
    fun cursorInAnotherEmptyPair_nestsTheNewOne() {
        assertEquals("~~**|**~~", toggle("~~|~~", MarkdownMarkers.Bold))
    }

    @Test
    fun cursorInAnEmptyBoldPair_isntInAnItalicPair() {
        assertEquals("***|***", toggle("**|**", MarkdownMarkers.Italic))
        assertEquals("|", toggle("**|**", MarkdownMarkers.Bold))
    }

    @Test
    fun selection_isWrappedAndStaysSelected() {
        assertEquals("Hello **[world]**", toggle("Hello [world]", MarkdownMarkers.Bold))
        assertEquals("~~[a b]~~ c", toggle("[a b] c", MarkdownMarkers.Strikethrough))
    }

    @Test
    fun selectionSelectedBackwards_isWrappedToo() {
        val result = TextWithSelection("Hello world", 11, 6).toggleMarkers(MarkdownMarkers.Bold)

        assertEquals("Hello **[world]**", result.marked())
    }

    @Test
    fun wrappedSelection_canBeWrappedAgain() {
        val bold = selection("Hello [world]").toggleMarkers(MarkdownMarkers.Bold)

        assertEquals("Hello ***[world]***", bold.toggleMarkers(MarkdownMarkers.Italic).marked())
    }

    @Test
    fun selectionInsideMarkers_unwraps() {
        assertEquals("Hello [world]", toggle("Hello **[world]**", MarkdownMarkers.Bold))
        assertEquals("[secret]", toggle(">![secret]!<", MarkdownMarkers.Spoiler))
    }

    @Test
    fun selectionWithItsMarkers_unwraps() {
        assertEquals("Hello [world]", toggle("Hello [**world**]", MarkdownMarkers.Bold))
    }

    @Test
    fun otherMarkers_areWrappedNotUnwrapped() {
        assertEquals("Hello ***[world]***", toggle("Hello **[world]**", MarkdownMarkers.Italic))
        assertEquals("*[**world**]*", toggle("[**world**]", MarkdownMarkers.Italic))
        assertEquals("~~[**world**]~~", toggle("[**world**]", MarkdownMarkers.Strikethrough))
    }

    @Test
    fun boldAndItalic_eachUnwrapTheirOwnMarkers() {
        assertEquals("**[world]**", toggle("***[world]***", MarkdownMarkers.Italic))
        assertEquals("*[world]*", toggle("***[world]***", MarkdownMarkers.Bold))
        assertEquals("[**world**]", toggle("[***world***]", MarkdownMarkers.Italic))
        assertEquals("[*world*]", toggle("[***world***]", MarkdownMarkers.Bold))
    }

    @Test
    fun spanWithOtherMarkersInside_unwraps() {
        assertEquals("[a **b** c]", toggle("[*a **b** c*]", MarkdownMarkers.Italic))
        assertEquals("[a *b* c]", toggle("[**a *b* c**]", MarkdownMarkers.Bold))
    }

    @Test
    fun separateSpans_areWrappedTogetherNotUnwrapped() {
        assertEquals("**[**a** and **b**]**", toggle("[**a** and **b**]", MarkdownMarkers.Bold))
        assertEquals(">![>!a!< >!b!<]!<", toggle("[>!a!< >!b!<]", MarkdownMarkers.Spoiler))
        assertEquals("*[*x* or *y*]*", toggle("[*x* or *y*]", MarkdownMarkers.Italic))
    }

    @Test
    fun spacesAtTheEdges_stayOutside() {
        assertEquals("Hello *[world]* again", toggle("Hello [world ]again", MarkdownMarkers.Italic))
        assertEquals("a  **[b]**  c", toggle("a [ b ] c", MarkdownMarkers.Bold))
    }

    @Test
    fun onlySpacesSelected_insertsAPairAtTheEnd() {
        assertEquals("a *|*b", toggle("a[ ]b", MarkdownMarkers.Italic))
    }

    @Test
    fun paragraphWithLineBreaks_isWrappedInOnePair() {
        assertEquals("*[one\ntwo]*", toggle("[one\ntwo]", MarkdownMarkers.Italic))
    }

    @Test
    fun severalParagraphs_areEachWrapped() {
        assertEquals("**[one**\n\n**two\nthree**\n \n**four]**", toggle("[one\n\ntwo\nthree\n \nfour]", MarkdownMarkers.Bold))
    }

    @Test
    fun severalWrappedParagraphs_areEachUnwrapped() {
        // The first paragraph's opening marker and the last one's closing marker are outside the selection.
        assertEquals("[one\n\ntwo]", toggle("**[one**\n\n**two]**", MarkdownMarkers.Bold))
        assertEquals("[one\n\ntwo]", toggle("[**one**\n\n**two**]", MarkdownMarkers.Bold))
    }

    @Test
    fun paragraphsOnlySomeWrapped_haveTheRestWrapped() {
        assertEquals("[**one**\n\n**two]**", toggle("[**one**\n\ntwo]", MarkdownMarkers.Bold))
    }

    @Test
    fun selectionStartingInABlankLine_skipsIt() {
        assertEquals("one\n\n*[two]*", toggle("one\n[\ntwo]", MarkdownMarkers.Italic))
    }

    @Test
    fun linkMarkdown_writesTheLink() {
        assertEquals("[Reddit](https://reddit.com)", linkMarkdown("Reddit", "https://reddit.com"))
    }

    @Test
    fun linkMarkdown_writesEmptyFieldsAsTheirLabels() {
        assertEquals("[display text](url)", linkMarkdown("", ""))
        assertEquals("[display text](https://a.com)", linkMarkdown("", "https://a.com"))
        assertEquals("[a](url)", linkMarkdown("a", "  "))
    }

    @Test
    fun linkMarkdown_escapesWhatWouldEndTheLinkEarly() {
        assertEquals(
            "[\\[1\\] a\\\\b](https://en.wikipedia.org/wiki/Fish_%28food%29%20x)",
            linkMarkdown("[1] a\\b", " https://en.wikipedia.org/wiki/Fish_(food) x "),
        )
    }

    @Test
    fun replaceSelection_putsTheTextInItsPlaceWithTheCursorAfter() {
        assertEquals("Hello [world](u)| again", selection("Hello [world] again").replaceSelection("[world](u)").marked())
        assertEquals("a[x](u)|b", selection("a|b").replaceSelection("[x](u)").marked())
    }

    @Test
    fun selectedText_isTheTextBetweenTheEnds() {
        assertEquals("world", TextWithSelection("Hello world", 11, 6).selectedText)
        assertEquals("", TextWithSelection("Hello", 2).selectedText)
    }
}
