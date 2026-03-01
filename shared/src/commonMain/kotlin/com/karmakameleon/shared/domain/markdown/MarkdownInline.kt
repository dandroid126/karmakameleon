package com.karmakameleon.shared.domain.markdown

sealed class MarkdownInline {
    data class Text(val text: String) : MarkdownInline()
    data class Bold(val children: List<MarkdownInline>) : MarkdownInline()
    data class Italic(val children: List<MarkdownInline>) : MarkdownInline()
    data class BoldItalic(val children: List<MarkdownInline>) : MarkdownInline()
    data class Strikethrough(val children: List<MarkdownInline>) : MarkdownInline()
    data class Code(val text: String) : MarkdownInline()
    data class Superscript(val children: List<MarkdownInline>) : MarkdownInline()
    data class Spoiler(val children: List<MarkdownInline>) : MarkdownInline()
    data class Link(val text: List<MarkdownInline>, val url: String) : MarkdownInline()
    data class Image(val url: String) : MarkdownInline()
}
