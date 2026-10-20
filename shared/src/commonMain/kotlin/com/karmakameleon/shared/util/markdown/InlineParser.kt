package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownInline

private val inlineImagePattern = Regex("""!\[img]\(([^)]+)\)""")
private val inlineGifPattern = Regex("""!\[gif]\(giphy\|([^)]+)\)""")
private val previewRedditUrlPattern = Regex("""https://preview\.redd\.it/[^\s)]+""")
private val escapeChars = setOf('\\', '*', '_', '~', '`', '>', '!', '<', '^', '[', ']', '(', ')', '#', '-', '.', '+', '|', '/')

fun parseInlineMarkdown(text: String): List<MarkdownInline> = parseInlineRecursive(text, emptySet())

private fun parseInlineRecursive(
    text: String,
    activeDelimiters: Set<String>,
): List<MarkdownInline> {
    val result = mutableListOf<MarkdownInline>()
    val cursor = InlineCursor(text, activeDelimiters)
    var textStart = 0
    while (cursor.i < text.length) {
        val match = inlineMatchers.firstNotNullOfOrNull { matcher -> cursor.matcher() }
        if (match == null) {
            cursor.i++
        } else {
            if (cursor.i > textStart) result.add(MarkdownInline.Text(text.substring(textStart, cursor.i)))
            result.add(match.node)
            cursor.i = match.end
            textStart = cursor.i
        }
    }
    if (text.length > textStart) result.add(MarkdownInline.Text(text.substring(textStart)))
    return result
}

// An inline element found at a position in the text, and the index just after it.
private class InlineMatch(
    val node: MarkdownInline,
    val end: Int,
)

// The text being parsed and the position the matchers look at. activeDelimiters holds the delimiters of the spans
// being parsed, so a span can't nest inside itself.
private class InlineCursor(
    val text: String,
    val activeDelimiters: Set<String>,
) {
    var i = 0
}

// Returns the element that starts at the cursor's position, or null.
private typealias InlineMatcher = InlineCursor.() -> InlineMatch?

// Tried in order at each position; the first match wins.
private val inlineMatchers: List<InlineMatcher> =
    listOf(
        InlineCursor::matchEscapedReference,
        InlineCursor::matchBackslashEscape,
        InlineCursor::matchInlineImage,
        InlineCursor::matchInlineGif,
        InlineCursor::matchInlineLink,
        InlineCursor::matchReferenceLink,
        InlineCursor::matchBoldItalic,
        InlineCursor::matchBold,
        InlineCursor::matchStrikethrough,
        InlineCursor::matchSpoiler,
        InlineCursor::matchParenthesizedSuperscript,
        InlineCursor::matchWordSuperscript,
        InlineCursor::matchItalic,
        InlineCursor::matchCode,
        InlineCursor::matchBareUrl,
        InlineCursor::matchRedditReference,
    )

// Escaped subreddit \/r/ or user \/u/ → plain text, no link
private fun InlineCursor.matchEscapedReference(): InlineMatch? {
    val prefix = listOf("\\/r/", "\\/u/").firstOrNull { text.startsWith(it, i) } ?: return null
    val nameStart = i + prefix.length
    val end = redditNameEnd(text, nameStart)
    return InlineMatch(MarkdownInline.Text(prefix.drop(1) + text.substring(nameStart, end)), end)
}

private fun InlineCursor.matchBackslashEscape(): InlineMatch? {
    val escaped = text.getOrNull(i + 1)
    if (text[i] != '\\' || escaped == null || escaped !in escapeChars) return null
    return InlineMatch(MarkdownInline.Text(escaped.toString()), i + 2)
}

// ![img](url), or ![img](id) for an image uploaded to Reddit
private fun InlineCursor.matchInlineImage(): InlineMatch? {
    val match = inlineImagePattern.matchAt(text, i) ?: return null
    return InlineMatch(MarkdownInline.Image(inlineImageUrl(match)), match.range.last + 1)
}

// ![gif](giphy|id) or ![gif](giphy|id|downsized)
private fun InlineCursor.matchInlineGif(): InlineMatch? {
    val match = inlineGifPattern.matchAt(text, i) ?: return null
    return InlineMatch(MarkdownInline.Image(inlineGifUrl(match)), match.range.last + 1)
}

// Link: [text](url)
private fun InlineCursor.matchInlineLink(): InlineMatch? {
    val closeTextIdx = linkTextEnd(next = '(') ?: return null
    val closeUrlIdx = text.indexOf(")", closeTextIdx + 2).takeIf { it != -1 } ?: return null
    val linkText = text.substring(i + 1, closeTextIdx)
    val url = text.substring(closeTextIdx + 2, closeUrlIdx)
    return InlineMatch(MarkdownInline.Link(parseInlineRecursive(linkText, activeDelimiters), url), closeUrlIdx + 1)
}

// Reference link: [text][ref]. References aren't resolved, so the text is used as the URL.
private fun InlineCursor.matchReferenceLink(): InlineMatch? {
    val closeTextIdx = linkTextEnd(next = '[') ?: return null
    val closeRefIdx = text.indexOf("]", closeTextIdx + 2).takeIf { it != -1 } ?: return null
    val linkText = text.substring(i + 1, closeTextIdx)
    return InlineMatch(MarkdownInline.Link(parseInlineRecursive(linkText, activeDelimiters), linkText), closeRefIdx + 1)
}

// The index of the ] that closes link text starting at the cursor, when it's followed by next.
private fun InlineCursor.linkTextEnd(next: Char): Int? {
    if (text[i] != '[') return null
    return text.indexOf("]", i + 1).takeIf { it != -1 && text.getOrNull(it + 1) == next }
}

// Bold+Italic: *** or ___
private fun InlineCursor.matchBoldItalic(): InlineMatch? {
    if ("***" in activeDelimiters || "___" in activeDelimiters) return null
    val delim = listOf("***", "___").firstOrNull { text.startsWith(it, i) } ?: return null
    return matchDelimited(delim, delim) { MarkdownInline.BoldItalic(it) }
}

// Bold: ** or __ (not *** or ___)
private fun InlineCursor.matchBold(): InlineMatch? {
    if ("**" in activeDelimiters || "__" in activeDelimiters) return null
    val delim = listOf("**", "__").firstOrNull { text.startsWith(it, i) && !text.startsWith(it + it[0], i) } ?: return null
    return matchDelimited(delim, delim, isValidClose = { !text.startsWith(delim + delim[0], it) }) {
        MarkdownInline.Bold(it)
    }
}

private fun InlineCursor.matchStrikethrough(): InlineMatch? = matchDelimited("~~", "~~") { MarkdownInline.Strikethrough(it) }

private fun InlineCursor.matchSpoiler(): InlineMatch? = matchDelimited(">!", "!<") { MarkdownInline.Spoiler(it) }

// Superscript: ^(...)
private fun InlineCursor.matchParenthesizedSuperscript(): InlineMatch? = matchDelimited("^(", ")") { MarkdownInline.Superscript(it) }

// Superscript: ^word, up to the next whitespace
private fun InlineCursor.matchWordSuperscript(): InlineMatch? {
    if (text[i] != '^' || "^" in activeDelimiters) return null
    val next = text.getOrNull(i + 1)
    if (next == null || next == '(') return null
    val end = (i + 1 until text.length).firstOrNull { text[it].isWhitespace() } ?: text.length
    return InlineMatch(MarkdownInline.Superscript(parseInlineRecursive(text.substring(i + 1, end), activeDelimiters + "^")), end)
}

// Italic: _..._ or *...* (not __ or **)
private fun InlineCursor.matchItalic(): InlineMatch? {
    val delim = listOf("_", "*").firstOrNull { text.startsWith(it, i) } ?: return null
    if (text.startsWith(delim + delim, i)) return null
    return matchDelimited(delim, delim, isValidClose = { !text.startsWith(delim + delim, it) }) {
        MarkdownInline.Italic(it)
    }
}

// Inline code: `...` (no parsing inside)
private fun InlineCursor.matchCode(): InlineMatch? {
    if (text[i] != '`' || "`" in activeDelimiters) return null
    val closeIdx = text.indexOf("`", i + 1)
    if (closeIdx == -1) return null
    return InlineMatch(MarkdownInline.Code(text.substring(i + 1, closeIdx)), closeIdx + 1)
}

// Bare URL: https:// or http://, shown as an image when it's a preview.redd.it image
private fun InlineCursor.matchBareUrl(): InlineMatch? {
    if (!text.startsWith("https://", i) && !text.startsWith("http://", i)) return null
    val end = (i until text.length).firstOrNull { text[it].isWhitespace() || text[it] == ')' } ?: text.length
    val url = text.substring(i, end)
    val httpsUrl = if (url.startsWith("http://")) url.replace("http://", "https://") else url
    val node =
        if (previewRedditUrlPattern.matches(httpsUrl)) {
            MarkdownInline.Image(httpsUrl)
        } else {
            MarkdownInline.Link(listOf(MarkdownInline.Text(url)), httpsUrl)
        }
    return InlineMatch(node, end)
}

// Subreddit or user reference: /r/name, r/name, /u/name or u/name. Without the slash it must start a word.
private fun InlineCursor.matchRedditReference(): InlineMatch? {
    val prefix = listOf("/r/", "/u/", "r/", "u/").firstOrNull { text.startsWith(it, i) } ?: return null
    if (!prefix.startsWith("/") && i > 0 && text[i - 1].isLetterOrDigit()) return null
    val nameStart = i + prefix.length
    val end = redditNameEnd(text, nameStart)
    val ref = prefix.removePrefix("/") + text.substring(nameStart, end)
    return InlineMatch(MarkdownInline.Link(listOf(MarkdownInline.Text(text.substring(i, end))), "https://www.reddit.com/$ref"), end)
}

// A span between an opening and closing delimiter, with its content parsed recursively. Fails when the delimiter
// is already open around this text, there's no closing delimiter, or the first one found isn't a valid close.
private inline fun InlineCursor.matchDelimited(
    open: String,
    close: String,
    isValidClose: (Int) -> Boolean = { true },
    node: (List<MarkdownInline>) -> MarkdownInline,
): InlineMatch? {
    if (!text.startsWith(open, i) || open in activeDelimiters) return null
    val closeIdx = text.indexOf(close, i + open.length)
    if (closeIdx == -1 || !isValidClose(closeIdx)) return null
    val children = parseInlineRecursive(text.substring(i + open.length, closeIdx), activeDelimiters + open)
    return InlineMatch(node(children), closeIdx + close.length)
}

// The end of a subreddit or user name starting at start.
private fun redditNameEnd(
    text: String,
    start: Int,
): Int = (start until text.length).firstOrNull { !text[it].isLetterOrDigit() && text[it] != '_' } ?: text.length

private fun inlineImageUrl(match: MatchResult): String {
    val content = match.groupValues[1]
    return if (content.startsWith("http")) content else "https://preview.redd.it/$content.jpeg"
}

private fun inlineGifUrl(match: MatchResult): String {
    val parts = match.groupValues[1].split("|")
    val suffix = if ("downsized" in parts.drop(1)) "giphy-downsized.gif" else "giphy.gif"
    return "https://i.giphy.com/${parts[0]}/$suffix"
}
