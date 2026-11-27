package com.karmakameleon.android.ui.components

/** Text with its selection, from [selectionStart] up to [selectionEnd]. They're equal when there's only a cursor. */
internal data class TextWithSelection(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart,
)

/** The markers a formatting button puts around text, like `**` and `**` for bold. */
internal data class MarkdownMarkers(
    val open: String,
    val close: String = open,
) {
    companion object {
        // Not _, which doesn't make italics inside a word.
        val Italic = MarkdownMarkers("*")
        val Bold = MarkdownMarkers("**")
        val Strikethrough = MarkdownMarkers("~~")
        val Spoiler = MarkdownMarkers(">!", "!<")
    }
}

/**
 * What tapping a formatting button does. With only a cursor, it puts an empty pair of [markers] there with the cursor
 * between them, or takes away the empty pair the cursor is in. With a selection, it wraps each paragraph of the
 * selection in a pair, leaving spaces at its edges outside, since Reddit's formatting can't cross a blank line and
 * doesn't start or end at a space. Paragraphs already wrapped, with the markers in the selection or just outside it,
 * are left as they are, and when every paragraph is, it takes the pairs away instead. Either way the same text stays
 * selected, so another button can wrap it again.
 */
internal fun TextWithSelection.toggleMarkers(markers: MarkdownMarkers): TextWithSelection {
    val start = minOf(selectionStart, selectionEnd)
    val end = maxOf(selectionStart, selectionEnd)
    val paragraphs = paragraphsIn(text, start, end)
    if (paragraphs.isEmpty()) return toggleEmptyPair(markers, end)
    val unwraps = paragraphs.map { markersAround(it, markers) }
    if (unwraps.all { it != null }) {
        val unwrapping = unwraps.filterNotNull()
        return applyEdits(text, unwrapping.flatMap { it.edits }, unwrapping.first().contentStart, unwrapping.last().contentEnd)
    }
    val edits =
        paragraphs.zip(unwraps).filter { (_, unwrap) -> unwrap == null }.flatMap { (paragraph, _) ->
            listOf(Edit(paragraph.first, paragraph.first, markers.open), Edit(paragraph.last, paragraph.last, markers.close))
        }
    return applyEdits(text, edits, paragraphs.first().first, paragraphs.last().last)
}

// first is the start and last is the end (exclusive) of each paragraph's text, without the spaces at its edges.
private fun paragraphsIn(
    text: String,
    start: Int,
    end: Int,
): List<IntRange> {
    val paragraphs = mutableListOf<IntRange>()
    var paragraphStart = -1
    var paragraphEnd = -1
    var lineStart = start
    while (true) {
        val lineEnd = text.indexOf('\n', lineStart).takeIf { it in 0 until end } ?: end
        if (text.substring(lineStart, lineEnd).isBlank()) {
            if (paragraphStart >= 0) paragraphs += paragraphStart..paragraphEnd
            paragraphStart = -1
        } else {
            if (paragraphStart < 0) paragraphStart = lineStart
            paragraphEnd = lineEnd
        }
        if (lineEnd >= end) break
        lineStart = lineEnd + 1
    }
    if (paragraphStart >= 0) paragraphs += paragraphStart..paragraphEnd
    return paragraphs.map { trimmed(text, it) }
}

private fun trimmed(
    text: String,
    range: IntRange,
): IntRange {
    var first = range.first
    var last = range.last
    while (text[first].isWhitespace()) first++
    while (text[last - 1].isWhitespace()) last--
    return first..last
}

// How to take away the markers around a paragraph, when it has them: in the selection, or just outside it.
private class Unwrap(
    val edits: List<Edit>,
    val contentStart: Int,
    val contentEnd: Int,
)

// The opening marker can be at the paragraph's start or just before it, and the closing one at its end or just after.
// They only count when they're a pair: `**a** and **b**` is two bold words, not one.
private fun TextWithSelection.markersAround(
    paragraph: IntRange,
    markers: MarkdownMarkers,
): Unwrap? {
    val first = paragraph.first
    val last = paragraph.last
    val open = markers.open.length
    val close = markers.close.length
    val openStart =
        when {
            text.isMarkerAt(markers.open, first) -> first
            first >= open && text.isMarkerAt(markers.open, first - open) -> first - open
            else -> return null
        }
    val closeEnd =
        when {
            last - close >= openStart + open && text.isMarkerAt(markers.close, last - close) -> last
            text.isMarkerAt(markers.close, last) -> last + close
            else -> return null
        }
    val content = text.substring(openStart + open, closeEnd - close)
    if (content.contains(markers.open) || content.contains(markers.close)) return null
    return Unwrap(
        edits = listOf(Edit(openStart, openStart + open, ""), Edit(closeEnd - close, closeEnd, "")),
        contentStart = openStart + open,
        contentEnd = closeEnd - close,
    )
}

private fun TextWithSelection.toggleEmptyPair(
    markers: MarkdownMarkers,
    cursor: Int,
): TextWithSelection {
    val open = markers.open.length
    // The markers touch, so each side's run is measured from the cursor.
    val inEmptyPair =
        cursor >= open &&
            text.substring(0, cursor).isMarkerAt(markers.open, cursor - open) &&
            text.substring(cursor).isMarkerAt(markers.close, 0)
    if (inEmptyPair) {
        return TextWithSelection(text.removeRange(cursor - open, cursor + markers.close.length), cursor - open)
    }
    return TextWithSelection(text.substring(0, cursor) + markers.open + markers.close + text.substring(cursor), cursor + open)
}

// Whether marker is at index, and not part of a longer run of its character: one * of a bold ** isn't an italic marker.
// A run of three, bold and italic at once, counts for both.
private fun String.isMarkerAt(
    marker: String,
    index: Int,
): Boolean {
    if (!startsWith(marker, index)) return false
    val char = marker[0]
    if (marker.any { it != char }) return true
    var runStart = index
    while (runStart > 0 && this[runStart - 1] == char) runStart--
    var runEnd = index + marker.length
    while (runEnd < length && this[runEnd] == char) runEnd++
    val run = runEnd - runStart
    return run == marker.length || run == BOLD_ITALIC_RUN
}

private const val BOLD_ITALIC_RUN = 3

// Replaces the text from start up to end with replacement.
private data class Edit(
    val start: Int,
    val end: Int,
    val replacement: String,
)

// Makes the edits, which don't overlap and are in order, and selects from selectionStart to selectionEnd as they were
// before the edits. Text put in at selectionStart goes before the selection, and text put in at selectionEnd after it.
private fun applyEdits(
    text: String,
    edits: List<Edit>,
    selectionStart: Int,
    selectionEnd: Int,
): TextWithSelection {
    val result = StringBuilder()
    var copied = 0
    for (edit in edits) {
        result.append(text, copied, edit.start).append(edit.replacement)
        copied = edit.end
    }
    result.append(text, copied, text.length)

    fun moved(
        offset: Int,
        includeEditsEndingHere: Boolean,
    ) = offset +
        edits
            .filter { it.end < offset || (it.end == offset && includeEditsEndingHere) }
            .sumOf { it.replacement.length - (it.end - it.start) }
    return TextWithSelection(
        result.toString(),
        moved(selectionStart, includeEditsEndingHere = true),
        moved(selectionEnd, includeEditsEndingHere = false),
    )
}

/**
 * The markdown for a link. An empty field is written as its label, so there's something to fill in. Characters that
 * would end the link early are escaped.
 */
internal fun linkMarkdown(
    displayText: String,
    url: String,
): String {
    val text =
        displayText
            .ifEmpty { LINK_TEXT_LABEL }
            .replace("\\", "\\\\")
            .replace("[", "\\[")
            .replace("]", "\\]")
    val address =
        url
            .trim()
            .ifEmpty { LINK_URL_LABEL }
            .replace("(", "%28")
            .replace(")", "%29")
            .replace(" ", "%20")
    return "[$text]($address)"
}

internal const val LINK_TEXT_LABEL = "display text"
internal const val LINK_URL_LABEL = "url"

/** This text with [markdown] in place of the selection, and the cursor after it. */
internal fun TextWithSelection.replaceSelection(markdown: String): TextWithSelection {
    val start = minOf(selectionStart, selectionEnd)
    val end = maxOf(selectionStart, selectionEnd)
    return TextWithSelection(text.substring(0, start) + markdown + text.substring(end), start + markdown.length)
}

/** The selected text. */
internal val TextWithSelection.selectedText: String
    get() = text.substring(minOf(selectionStart, selectionEnd), maxOf(selectionStart, selectionEnd))
