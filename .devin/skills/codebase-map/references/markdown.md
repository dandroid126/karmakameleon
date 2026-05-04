# Markdown System Reference

Custom markdown parser (not a library) that targets Reddit's flavor: supports GFM tables, code blocks (fenced + indented), spoilers (`>!...!<`), nested lists, super/subscript, strikethrough, inline code, block quotes, reddit-specific link forms.

## Files

### Parser (shared, KMP) - `shared/src/commonMain/kotlin/com/karmakameleon/shared/util/markdown/`
- `MarkdownParser.kt` - public entry: `parseMarkdown(source: String): List<MarkdownBlock>`.
- `BlockParser.kt` - splits the source into block-level AST (`MarkdownBlock` subtypes).
- `InlineParser.kt` - parses inline spans (`MarkdownInline` subtypes) within each block.

### AST - `shared/src/commonMain/kotlin/com/karmakameleon/shared/domain/markdown/`
- `MarkdownBlock.kt` - sealed class. Subtypes include: Paragraph, Heading, CodeBlock, BlockQuote, UnorderedList, OrderedList, ListItem, Table, HorizontalRule, etc.
- `MarkdownInline.kt` - sealed class. Subtypes include: Text, Bold, Italic, Strikethrough, Code, Link, Image, Spoiler, Superscript, Subscript, etc.

### Rendering (Android Compose) - `androidApp/src/main/kotlin/com/karmakameleon/android/ui/components/`
- `MarkdownText.kt` - takes a markdown string or pre-parsed `List<MarkdownBlock>`, renders with Compose. Handles click routing through the injected `NavigationHandler` for Reddit-flavored links.

### Tests - `shared/src/commonTest/kotlin/com/karmakameleon/shared/util/markdown/`
- `BlockParserTest.kt`
- `InlineParserTest.kt`

## Flow

```
raw markdown (from PostDto.selftext / CommentDto.body)
  -> MarkdownParser.parseMarkdown(source)
  -> List<MarkdownBlock>  (AST with nested List<MarkdownInline>)
  -> MarkdownText(blocks = ...) composable
  -> Compose Text/Column/Row/Canvas rendering
```

Link clicks in the rendered output call `NavigationHandler.handleLink(url)`, which resolves via `parseRedditLink(url)` in `LinkParser.kt` and routes to the appropriate screen.

## Extending the parser

1. If adding a new block type: add a subtype to `MarkdownBlock` sealed class; handle in `BlockParser`; render in `MarkdownText`.
2. If adding a new inline type: add a subtype to `MarkdownInline`; handle in `InlineParser`; render in `MarkdownText`.
3. Always add parser tests in `BlockParserTest.kt` / `InlineParserTest.kt`.
4. Render changes should be previewed on both light and dark themes (colors come from `MaterialTheme.colorScheme`).
