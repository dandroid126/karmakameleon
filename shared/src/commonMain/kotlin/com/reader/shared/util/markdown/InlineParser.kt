package com.reader.shared.util.markdown

import com.reader.shared.domain.markdown.MarkdownInline

private val inlineImagePattern = Regex("""!\[img]\(([^)]+)\)""")
private val inlineGifPattern = Regex("""!\[gif]\(giphy\|([^)]+)\)""")
private val previewRedditUrlPattern = Regex("""https://preview\.redd\.it/[^\s)]+""")
private val escapeChars = setOf('\\', '*', '_', '~', '`', '>', '!', '<', '^', '[', ']', '(', ')', '#', '-', '.', '+', '|', '/')

fun parseInlineMarkdown(text: String): List<MarkdownInline> {
    return parseInlineRecursive(text, emptySet())
}

private fun parseInlineRecursive(text: String, activeDelimiters: Set<String>): List<MarkdownInline> {
    val result = mutableListOf<MarkdownInline>()
    var i = 0
    var textStart = i

    fun flushText(end: Int) {
        if (end > textStart) {
            result.add(MarkdownInline.Text(text.substring(textStart, end)))
        }
    }

    while (i < text.length) {
        // Escaped subreddit \/r/ or user \/u/ → plain text, no link
        if (text.startsWith("\\/r/", i)) {
            flushText(i)
            val nameStart = i + 4
            val endIdx = (nameStart until text.length).firstOrNull { !text[it].isLetterOrDigit() && text[it] != '_' } ?: text.length
            result.add(MarkdownInline.Text("/r/" + text.substring(nameStart, endIdx)))
            i = endIdx
            textStart = i
            continue
        }
        if (text.startsWith("\\/u/", i)) {
            flushText(i)
            val nameStart = i + 4
            val endIdx = (nameStart until text.length).firstOrNull { !text[it].isLetterOrDigit() && text[it] != '_' } ?: text.length
            result.add(MarkdownInline.Text("/u/" + text.substring(nameStart, endIdx)))
            i = endIdx
            textStart = i
            continue
        }

        // Backslash escape
        if (text.startsWith("\\", i) && i + 1 < text.length && text[i + 1] in escapeChars) {
            flushText(i)
            result.add(MarkdownInline.Text(text[i + 1].toString()))
            i += 2
            textStart = i
            continue
        }

        // Inline image: ![img](url) or ![gif](giphy|id)
        val imgMatch = inlineImagePattern.find(text, i)
        if (imgMatch != null && imgMatch.range.first == i) {
            flushText(i)
            val content = imgMatch.groupValues[1]
            val url = if (content.startsWith("http")) content else "https://preview.redd.it/${content}.jpeg"
            result.add(MarkdownInline.Image(url))
            i = imgMatch.range.last + 1
            textStart = i
            continue
        }
        val gifMatch = inlineGifPattern.find(text, i)
        if (gifMatch != null && gifMatch.range.first == i) {
            flushText(i)
            val parts = gifMatch.groupValues[1].split("|")
            val gifId = parts[0]
            val params = parts.drop(1)
            val suffix = if ("downsized" in params) "giphy-downsized.gif" else "giphy.gif"
            result.add(MarkdownInline.Image("https://i.giphy.com/${gifId}/${suffix}"))
            i = gifMatch.range.last + 1
            textStart = i
            continue
        }

        // Links: [text](url) and [text][ref]
        if (text.startsWith("[", i)) {
            val closeTextIdx = text.indexOf("]", i + 1)
            if (closeTextIdx != -1) {
                when {
                    text.getOrNull(closeTextIdx + 1) == '(' -> {
                        val closeUrlIdx = text.indexOf(")", closeTextIdx + 2)
                        if (closeUrlIdx != -1) {
                            flushText(i)
                            val linkText = text.substring(i + 1, closeTextIdx)
                            val linkUrl = text.substring(closeTextIdx + 2, closeUrlIdx)
                            val linkChildren = parseInlineRecursive(linkText, activeDelimiters)
                            result.add(MarkdownInline.Link(linkChildren, linkUrl))
                            i = closeUrlIdx + 1
                            textStart = i
                            continue
                        }
                    }
                    text.getOrNull(closeTextIdx + 1) == '[' -> {
                        val refCloseIdx = text.indexOf("]", closeTextIdx + 2)
                        if (refCloseIdx != -1) {
                            flushText(i)
                            val linkText = text.substring(i + 1, closeTextIdx)
                            val linkChildren = parseInlineRecursive(linkText, activeDelimiters)
                            result.add(MarkdownInline.Link(linkChildren, linkText))
                            i = refCloseIdx + 1
                            textStart = i
                            continue
                        }
                    }
                    else -> {}
                }
            }
        }

        // Bold+Italic: *** or ___
        if ((text.startsWith("***", i) || text.startsWith("___", i)) && "***" !in activeDelimiters && "___" !in activeDelimiters) {
            val delim = text.substring(i, i + 3)
            val closeIdx = text.indexOf(delim, i + 3)
            if (closeIdx != -1) {
                flushText(i)
                val inner = text.substring(i + 3, closeIdx)
                val children = parseInlineRecursive(inner, activeDelimiters + delim)
                result.add(MarkdownInline.BoldItalic(children))
                i = closeIdx + 3
                textStart = i
                continue
            }
        }

        // Bold: ** or __
        if ((text.startsWith("**", i) || text.startsWith("__", i)) &&
            !text.startsWith("***", i) && !text.startsWith("___", i) &&
            "**" !in activeDelimiters && "__" !in activeDelimiters) {
            val delim = text.substring(i, i + 2)
            val closeIdx = text.indexOf(delim, i + 2)
            if (closeIdx != -1 && !text.startsWith(delim + delim[0], closeIdx)) {
                flushText(i)
                val inner = text.substring(i + 2, closeIdx)
                val children = parseInlineRecursive(inner, activeDelimiters + delim)
                result.add(MarkdownInline.Bold(children))
                i = closeIdx + 2
                textStart = i
                continue
            }
        }

        // Strikethrough: ~~
        if (text.startsWith("~~", i) && "~~" !in activeDelimiters) {
            val closeIdx = text.indexOf("~~", i + 2)
            if (closeIdx != -1) {
                flushText(i)
                val inner = text.substring(i + 2, closeIdx)
                val children = parseInlineRecursive(inner, activeDelimiters + "~~")
                result.add(MarkdownInline.Strikethrough(children))
                i = closeIdx + 2
                textStart = i
                continue
            }
        }

        // Spoiler: >!...!<
        if (text.startsWith(">!", i) && ">!" !in activeDelimiters) {
            val closeIdx = text.indexOf("!<", i + 2)
            if (closeIdx != -1) {
                flushText(i)
                val inner = text.substring(i + 2, closeIdx)
                val children = parseInlineRecursive(inner, activeDelimiters + ">!")
                result.add(MarkdownInline.Spoiler(children))
                i = closeIdx + 2
                textStart = i
                continue
            }
        }

        // Superscript: ^(...)
        if (text.startsWith("^(", i) && "^(" !in activeDelimiters) {
            val closeIdx = text.indexOf(")", i + 2)
            if (closeIdx != -1) {
                flushText(i)
                val inner = text.substring(i + 2, closeIdx)
                val children = parseInlineRecursive(inner, activeDelimiters + "^(")
                result.add(MarkdownInline.Superscript(children))
                i = closeIdx + 1
                textStart = i
                continue
            }
        }

        // Superscript: ^word (not followed by '(')
        if (text.startsWith("^", i) && i + 1 < text.length && text[i + 1] != '(' && "^" !in activeDelimiters) {
            val endIdx = (i + 1 until text.length).firstOrNull { text[it].isWhitespace() } ?: text.length
            flushText(i)
            val inner = text.substring(i + 1, endIdx)
            val children = parseInlineRecursive(inner, activeDelimiters + "^")
            result.add(MarkdownInline.Superscript(children))
            i = endIdx
            textStart = i
            continue
        }

        // Italic: _..._ (not __)
        if (text.startsWith("_", i) && !text.startsWith("__", i) && "_" !in activeDelimiters) {
            val closeIdx = text.indexOf("_", i + 1)
            if (closeIdx != -1 && !text.startsWith("__", closeIdx)) {
                flushText(i)
                val inner = text.substring(i + 1, closeIdx)
                val children = parseInlineRecursive(inner, activeDelimiters + "_")
                result.add(MarkdownInline.Italic(children))
                i = closeIdx + 1
                textStart = i
                continue
            }
        }

        // Italic: *...* (not ** or ***)
        if (text.startsWith("*", i) && !text.startsWith("**", i) && "*" !in activeDelimiters) {
            val closeIdx = text.indexOf("*", i + 1)
            if (closeIdx != -1 && !text.startsWith("**", closeIdx)) {
                flushText(i)
                val inner = text.substring(i + 1, closeIdx)
                val children = parseInlineRecursive(inner, activeDelimiters + "*")
                result.add(MarkdownInline.Italic(children))
                i = closeIdx + 1
                textStart = i
                continue
            }
        }

        // Inline code: `...` (no recursive parsing inside)
        if (text.startsWith("`", i) && "`" !in activeDelimiters) {
            val closeIdx = text.indexOf("`", i + 1)
            if (closeIdx != -1) {
                flushText(i)
                result.add(MarkdownInline.Code(text.substring(i + 1, closeIdx)))
                i = closeIdx + 1
                textStart = i
                continue
            }
        }

        // Bare URL: https:// or http://
        if (text.startsWith("https://", i) || text.startsWith("http://", i)) {
            val endIdx = (i until text.length).firstOrNull { text[it].isWhitespace() || text[it] == ')' } ?: text.length
            val url = text.substring(i, endIdx)
            val httpsUrl = if (url.startsWith("http://")) url.replace("http://", "https://") else url
            flushText(i)
            if (previewRedditUrlPattern.matches(httpsUrl)) {
                result.add(MarkdownInline.Image(httpsUrl))
            } else {
                result.add(MarkdownInline.Link(listOf(MarkdownInline.Text(url)), httpsUrl))
            }
            i = endIdx
            textStart = i
            continue
        }

        // Subreddit reference: /r/name or r/name
        if (text.startsWith("/r/", i)) {
            val nameStart = i + 3
            val endIdx = (nameStart until text.length).firstOrNull { !text[it].isLetterOrDigit() && text[it] != '_' } ?: text.length
            flushText(i)
            val ref = "r/" + text.substring(nameStart, endIdx)
            result.add(MarkdownInline.Link(listOf(MarkdownInline.Text(text.substring(i, endIdx))), "https://www.reddit.com/$ref"))
            i = endIdx
            textStart = i
            continue
        }
        if (text.startsWith("r/", i) && (i == 0 || !text[i - 1].isLetterOrDigit())) {
            val endIdx = (i + 2 until text.length).firstOrNull { !text[it].isLetterOrDigit() && text[it] != '_' } ?: text.length
            val ref = text.substring(i, endIdx)
            flushText(i)
            result.add(MarkdownInline.Link(listOf(MarkdownInline.Text(ref)), "https://www.reddit.com/$ref"))
            i = endIdx
            textStart = i
            continue
        }

        // User reference: /u/name or u/name
        if (text.startsWith("/u/", i)) {
            val nameStart = i + 3
            val endIdx = (nameStart until text.length).firstOrNull { !text[it].isLetterOrDigit() && text[it] != '_' } ?: text.length
            flushText(i)
            val ref = "u/" + text.substring(nameStart, endIdx)
            result.add(MarkdownInline.Link(listOf(MarkdownInline.Text(text.substring(i, endIdx))), "https://www.reddit.com/$ref"))
            i = endIdx
            textStart = i
            continue
        }
        if (text.startsWith("u/", i) && (i == 0 || !text[i - 1].isLetterOrDigit())) {
            val endIdx = (i + 2 until text.length).firstOrNull { !text[it].isLetterOrDigit() && text[it] != '_' } ?: text.length
            val ref = text.substring(i, endIdx)
            flushText(i)
            result.add(MarkdownInline.Link(listOf(MarkdownInline.Text(ref)), "https://www.reddit.com/$ref"))
            i = endIdx
            textStart = i
            continue
        }

        i++
    }

    flushText(i)
    return result
}

fun extractImageUrls(text: String): List<String> {
    val urls = mutableListOf<String>()
    for (match in inlineImagePattern.findAll(text)) {
        val content = match.groupValues[1]
        urls.add(if (content.startsWith("http")) content else "https://preview.redd.it/${content}.jpeg")
    }
    for (match in inlineGifPattern.findAll(text)) {
        val parts = match.groupValues[1].split("|")
        val gifId = parts[0]
        val params = parts.drop(1)
        val suffix = if ("downsized" in params) "giphy-downsized.gif" else "giphy.gif"
        urls.add("https://i.giphy.com/${gifId}/${suffix}")
    }
    for (match in previewRedditUrlPattern.findAll(text)) {
        if (urls.none { it == match.value }) {
            urls.add(match.value)
        }
    }
    return urls
}
