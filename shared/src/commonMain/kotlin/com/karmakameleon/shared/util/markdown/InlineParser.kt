package com.karmakameleon.shared.util.markdown

import com.karmakameleon.shared.domain.markdown.MarkdownInline

private val inlineImagePattern = Regex("""!\[img]\(([^)]+)\)""")
private val inlineGifPattern = Regex("""!\[gif]\(giphy\|([^)]+)\)""")
private val previewRedditUrlPattern = Regex("""https://preview\.redd\.it/[^\s)]+""")
private val whitespacePattern = Regex("""\s+""")

// <url> with a scheme Reddit links, or <mailto:…>. Inside the brackets the URL can't have spaces or brackets.
private val angleAutolinkPattern = Regex("""<((?:https?|ftp|git|steam|ircs?|news|mumble|ssh|ts3server)://[^\s<>]+|mailto:[^\s<>]+)>""")

// <me@example.com>, which links to mailto:me@example.com. Like on Reddit, the address can only have letters, digits,
// ".", "-", "_" and "+", so <foo://bar@x.com> isn't one.
private val angleEmailAutolinkPattern = Regex("""<([\w.+-]+@[\w-]+(?:\.[\w-]+)+)>""")
private val escapeChars = setOf('\\', '*', '_', '~', '`', '>', '!', '<', '^', '[', ']', '(', ')', '#', '-', '.', '+', '|', '/')

// references holds the URLs of reference links, by their normalized label (see normalizeLinkLabel).
fun parseInlineMarkdown(
    text: String,
    references: Map<String, String> = emptyMap(),
): List<MarkdownInline> = parseInlineRecursive(text, emptySet(), superscriptDepth = 0, references)

// A reference's label matches whatever its case and spacing, so [Foo  Bar] uses the definition of [foo bar].
internal fun normalizeLinkLabel(label: String): String =
    label
        .trim()
        .split(whitespacePattern)
        .joinToString(" ")
        .lowercase()

// The most superscripts that nest. Each level is drawn higher without making the line taller, so a deeper tower
// would draw over the line above. It also keeps the recursion shallow however many carets a comment has.
private const val MAX_SUPERSCRIPT_DEPTH = 3

private fun parseInlineRecursive(
    text: String,
    activeDelimiters: Set<String>,
    superscriptDepth: Int,
    references: Map<String, String>,
): List<MarkdownInline> {
    val result = mutableListOf<MarkdownInline>()
    val cursor = InlineCursor(text, activeDelimiters, superscriptDepth, references)
    var textStart = 0
    while (cursor.i < text.length) {
        val match = inlineMatchers.firstNotNullOfOrNull { matcher -> cursor.matcher() }
        if (match == null) {
            cursor.i++
        } else {
            if (cursor.i > textStart) result.add(MarkdownInline.Text(text.substring(textStart, cursor.i)))
            match.node?.let { result.add(it) }
            cursor.i = match.end
            textStart = cursor.i
        }
    }
    if (text.length > textStart) result.add(MarkdownInline.Text(text.substring(textStart)))
    return result
}

// An inline element found at a position in the text, and the index just after it. A null node drops the matched
// text.
private class InlineMatch(
    val node: MarkdownInline?,
    val end: Int,
)

// The text being parsed and the position the matchers look at. activeDelimiters holds the delimiters of the spans
// being parsed, so a span can't nest inside itself, superscriptDepth counts the superscripts around the text, and
// references holds the URLs reference links can use.
private class InlineCursor(
    val text: String,
    val activeDelimiters: Set<String>,
    val superscriptDepth: Int,
    val references: Map<String, String>,
) {
    var i = 0

    // The inline elements in text, which is inside the text being parsed.
    fun parseNested(
        text: String,
        activeDelimiters: Set<String> = this.activeDelimiters,
        superscriptDepth: Int = this.superscriptDepth,
    ): List<MarkdownInline> = parseInlineRecursive(text, activeDelimiters, superscriptDepth, references)
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
        InlineCursor::matchAngleAutolink,
        InlineCursor::matchAngleEmailAutolink,
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

// Link: [text](url), or [text](url "title") with the title in double quotes, single quotes or parentheses. The title
// is a tooltip on Reddit, so it's dropped.
private fun InlineCursor.matchInlineLink(): InlineMatch? {
    val closeTextIdx = linkTextEnd(next = '(') ?: return null
    val (url, end) = linkDestination(closeTextIdx + 2) ?: return null
    val linkText = text.substring(i + 1, closeTextIdx)
    return InlineMatch(MarkdownInline.Link(parseNested(linkText), url), end)
}

private val linkTitleClosers = mapOf('"' to '"', '\'' to '\'', '(' to ')')

// The URL of a link destination starting at start, and the index just after the ) that closes it. A title after the
// URL is skipped. When the text up to the ) isn't a URL and a title, all of it is the URL.
private fun InlineCursor.linkDestination(start: Int): Pair<String, Int>? {
    var idx = start
    while (text.getOrNull(idx)?.isWhitespace() == true) idx++
    val urlStart = idx
    while (idx < text.length && !text[idx].isWhitespace() && text[idx] != ')') idx++
    val urlEnd = idx
    while (text.getOrNull(idx)?.isWhitespace() == true) idx++
    // A title needs space before it, so a quote or ( in the URL itself isn't taken for one.
    val closer = linkTitleClosers[text.getOrNull(idx)]
    if (closer != null && idx > urlEnd) {
        val titleEnd = text.indexOf(closer, idx + 1)
        idx = if (titleEnd == -1) text.length else titleEnd + 1
        while (text.getOrNull(idx)?.isWhitespace() == true) idx++
    }
    return if (text.getOrNull(idx) == ')') text.substring(urlStart, urlEnd) to idx + 1 else wholeLinkDestination(start)
}

// All the text from start to the next ) as the URL, and the index just after the ). Null without a ).
private fun InlineCursor.wholeLinkDestination(start: Int): Pair<String, Int>? {
    val closeIdx = text.indexOf(')', start)
    return if (closeIdx == -1) null else text.substring(start, closeIdx) to closeIdx + 1
}

// Reference link: [text][ref], [text][] with the text as the label, or [text] alone. It links to the URL of the
// definition with that label, like [ref]: https://example.com on its own line. Without one, it stays as text.
private fun InlineCursor.matchReferenceLink(): InlineMatch? {
    val closeTextIdx = linkTextEnd(next = null) ?: return null
    val linkText = text.substring(i + 1, closeTextIdx)
    val closeRefIdx = if (text.getOrNull(closeTextIdx + 1) == '[') text.indexOf("]", closeTextIdx + 2) else -1
    val (label, end) =
        when {
            closeRefIdx == -1 -> linkText to closeTextIdx + 1
            closeRefIdx == closeTextIdx + 2 -> linkText to closeRefIdx + 1
            else -> text.substring(closeTextIdx + 2, closeRefIdx) to closeRefIdx + 1
        }
    val url = references[normalizeLinkLabel(label)] ?: return null
    return InlineMatch(MarkdownInline.Link(parseNested(linkText), url), end)
}

// The index of the ] that closes link text starting at the cursor, when it's followed by next, or by anything when
// next is null.
private fun InlineCursor.linkTextEnd(next: Char?): Int? {
    if (text[i] != '[') return null
    return text.indexOf("]", i + 1).takeIf { it != -1 && (next == null || text.getOrNull(it + 1) == next) }
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

// Superscript: ^(...). Past the deepest superscript, the parentheses stay as text.
private fun InlineCursor.matchParenthesizedSuperscript(): InlineMatch? {
    if (superscriptDepth >= MAX_SUPERSCRIPT_DEPTH) return null
    return matchDelimited("^(", ")", childSuperscriptDepth = superscriptDepth + 1) { MarkdownInline.Superscript(it) }
}

// Superscript: ^word, up to the next whitespace. Like old Reddit, a ^ inside it raises the rest again, so ^^double
// and 2^2^2 stack up. Past the deepest superscript, the ^ is dropped and the rest isn't raised further. A ^ before
// whitespace or at the end stays as text.
private fun InlineCursor.matchWordSuperscript(): InlineMatch? {
    if (text[i] != '^') return null
    val next = text.getOrNull(i + 1)
    if (next == null || next == '(' || next.isWhitespace()) return null
    if (superscriptDepth >= MAX_SUPERSCRIPT_DEPTH) return InlineMatch(null, i + 1)
    val end = (i + 1 until text.length).firstOrNull { text[it].isWhitespace() } ?: text.length
    val children = parseNested(text.substring(i + 1, end), superscriptDepth = superscriptDepth + 1)
    return InlineMatch(MarkdownInline.Superscript(children), end)
}

// Italic: _..._ or *...* (not __ or **)
private fun InlineCursor.matchItalic(): InlineMatch? {
    val delim = listOf("_", "*").firstOrNull { text.startsWith(it, i) } ?: return null
    if (text.startsWith(delim + delim, i)) return null
    return matchDelimited(delim, delim, isValidClose = { !text.startsWith(delim + delim, it) }) {
        MarkdownInline.Italic(it)
    }
}

// Inline code: `...`, or ``...`` and longer runs, which can contain shorter runs of backticks (no parsing inside). As
// in CommonMark, the code ends at the next run of exactly as many backticks; without one, the opening run is text. A
// line break inside is a space, and when the code starts and ends with a space, one is dropped from each end, so
// `` `a` `` is the code "`a`".
private fun InlineCursor.matchCode(): InlineMatch? {
    if (text[i] != '`') return null
    val openEnd = backtickRunEnd(i)
    val runLength = openEnd - i
    var closeIdx = text.indexOf('`', openEnd)
    while (closeIdx != -1 && backtickRunEnd(closeIdx) - closeIdx != runLength) {
        closeIdx = text.indexOf('`', backtickRunEnd(closeIdx))
    }
    if (closeIdx == -1) return InlineMatch(MarkdownInline.Text(text.substring(i, openEnd)), openEnd)
    var code = text.substring(openEnd, closeIdx).replace('\n', ' ')
    if (code.startsWith(' ') && code.endsWith(' ') && code.isNotBlank()) {
        code = code.substring(1, code.length - 1)
    }
    return InlineMatch(MarkdownInline.Code(code), closeIdx + runLength)
}

private fun InlineCursor.backtickRunEnd(start: Int): Int = (start until text.length).firstOrNull { text[it] != '`' } ?: text.length

// Bare URL: https:// or http://, shown as an image when it's a preview.redd.it image
private fun InlineCursor.matchBareUrl(): InlineMatch? {
    if (!text.startsWith("https://", i) && !text.startsWith("http://", i)) return null
    val end = (i until text.length).firstOrNull { text[it].isWhitespace() || text[it] == ')' } ?: text.length
    return InlineMatch(urlNode(text.substring(i, end)), end)
}

// Autolink: <https://example.com>, shown without the brackets. Like on Reddit, <mailto:…> shows only the address.
private fun InlineCursor.matchAngleAutolink(): InlineMatch? {
    val match = angleAutolinkPattern.matchAt(text, i) ?: return null
    val url = match.groupValues[1]
    val node = if (url.startsWith(MAILTO)) mailtoLink(url.removePrefix(MAILTO)) else urlNode(url)
    return InlineMatch(node, match.range.last + 1)
}

// Email autolink: <me@example.com>, shown without the brackets.
private fun InlineCursor.matchAngleEmailAutolink(): InlineMatch? {
    val match = angleEmailAutolinkPattern.matchAt(text, i) ?: return null
    return InlineMatch(mailtoLink(match.groupValues[1]), match.range.last + 1)
}

private const val MAILTO = "mailto:"

// A link that shows an email address and opens it as mailto:.
private fun mailtoLink(address: String): MarkdownInline = MarkdownInline.Link(listOf(MarkdownInline.Text(address)), MAILTO + address)

// A URL typed in the text, as a link that shows it, opened as https when it's http. A preview.redd.it image is shown.
private fun urlNode(url: String): MarkdownInline {
    val httpsUrl = if (url.startsWith("http://")) url.replace("http://", "https://") else url
    return if (previewRedditUrlPattern.matches(httpsUrl)) {
        MarkdownInline.Image(httpsUrl)
    } else {
        MarkdownInline.Link(listOf(MarkdownInline.Text(url)), httpsUrl)
    }
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
// is already open around this text or there's no valid closing delimiter. A run of delimiter characters that isn't
// a valid close is skipped whole, so _this is __really__ important_ is italic with bold inside, and a nested span
// with the same character doesn't end the outer one.
// Like Reddit, underscores inside a word are text: a run of them can't open a span after a letter or digit, and the
// close is the first one whose run isn't followed by a letter or digit, so snake_case_names stays as typed and
// _foo_bar_ is italic.
private inline fun InlineCursor.matchDelimited(
    open: String,
    close: String,
    isValidClose: (Int) -> Boolean = { true },
    childSuperscriptDepth: Int = superscriptDepth,
    node: (List<MarkdownInline>) -> MarkdownInline,
): InlineMatch? {
    if (!text.startsWith(open, i) || open in activeDelimiters) return null
    val underscores = open.all { it == '_' }
    if (underscores && isWordCharBeforeUnderscores(i)) return null
    var closeIdx = text.indexOf(close, i + open.length)
    while (closeIdx != -1) {
        closeIdx =
            when {
                !isValidClose(closeIdx) -> text.indexOf(close, delimiterRunEnd(closeIdx))
                underscores && isWordCharAfterUnderscores(closeIdx + close.length) -> text.indexOf(close, closeIdx + 1)
                else -> break
            }
    }
    if (closeIdx == -1) return null
    val children = parseNested(text.substring(i + open.length, closeIdx), activeDelimiters + open, childSuperscriptDepth)
    return InlineMatch(node(children), closeIdx + close.length)
}

// The index just after the run of the character at start.
private fun InlineCursor.delimiterRunEnd(start: Int): Int = (start until text.length).firstOrNull { text[it] != text[start] } ?: text.length

// Whether a letter or digit comes before the underscores that end just before index.
private fun InlineCursor.isWordCharBeforeUnderscores(index: Int): Boolean {
    var before = index - 1
    while (text.getOrNull(before) == '_') before--
    return text.getOrNull(before)?.isLetterOrDigit() == true
}

// Whether a letter or digit comes after the underscores that start at index.
private fun InlineCursor.isWordCharAfterUnderscores(index: Int): Boolean {
    var after = index
    while (text.getOrNull(after) == '_') after++
    return text.getOrNull(after)?.isLetterOrDigit() == true
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
