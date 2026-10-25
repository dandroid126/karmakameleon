# Reddit markdown reference

Reddit's markdown isn't standard markdown. This file lists each rule, what Reddit renders, and what this app does, so changes to the parser (`shared/.../util/markdown/`) and the renderer (`androidApp/.../ui/components/MarkdownText.kt`) can be checked against Reddit instead of against intuition.

## Sources

- Reddit's [Formatting Guide](https://support.reddithelp.com/hc/en-us/articles/360043033952-Formatting-Guide), which shows each example as written and as rendered, and notes where old Reddit differs. It covers common syntax, not everything. Its site refuses automated requests, so read it in a browser.
- [A test post](https://www.reddit.com/user/dandroid126/comments/1rcxkd3/test/) with examples of the rules below, to compare the app with Reddit side by side. Rules marked ❔ should be added to it. Only the account's owner posts to it, by hand: posting from scripts or tools could get the account flagged as a bot.
- Where the guide and the test post are silent, the app follows new Reddit (www.reddit.com and the official apps), not old Reddit.

## Status

- ✅ The app matches Reddit.
- ❌ The app differs; the issue tracks the fix.
- ❔ Unverified: what Reddit renders hasn't been checked yet.

When a fix lands, update its row here and add the case to the parser tests and, for anything visible, to `MarkdownTextScreenshotTest` in `androidApp/src/test/.../ui/components/` (added by #85), whose comment lists the cases it leaves out until they're fixed.

## Paragraphs and line breaks

| Rule | Written | Reddit | App |
|---|---|---|---|
| Paragraphs are separated by a blank line | `one` (blank line) `two` | Two paragraphs | ✅ |
| Extra blank lines add no extra space | `one` (3 blank lines) `two` | Same spacing as one blank line | ✅ |
| A single line break joins the lines | `one`⏎`two` | "one two" | ✅ |
| Two spaces at the end of a line make a hard break | `one␣␣`⏎`two` | Two lines | ✅ |
| A backslash at the end of a line makes a hard break (new Reddit only) | `one\`⏎`two` | Two lines | ❌ #83: shows the backslash and joins the lines |
| A paragraph of only `&nbsp;` makes an empty line | `one` ⏎⏎ `&nbsp;` ⏎⏎ `two` | An empty paragraph between them | ❌ #83: `decodeHtml` turns `&nbsp;` into a normal space, so the line counts as blank and merges with the blank lines around it |

## Emphasis

| Rule | Written | Reddit | App |
|---|---|---|---|
| Italics | `*italic*` or `_italic_` | *italic* | ✅ |
| Bold | `**bold**` or `__bold__` | **bold** | ✅ |
| Bold italics | `***both***` or `___both___` | ***both*** | ✅ |
| Strikethrough | `~~struck~~` | ~~struck~~ | ✅ |
| Emphasis inside a word works with `*` | `vul*pu*ta**te**` | vul*pu*ta**te** | ✅ |
| …but not with `_` | `snake_case_names` | snake_case_names | ✅ |
| Nested emphasis (new Reddit only) | `_this is __really__ important_` | *this is **really** important* | ❌ #83: the outer underscores stay as text |
| Escapes | `\*not italic\*`, `\- not a list` | The characters as typed | ✅ |

## Reddit-only inline syntax

| Rule | Written | Reddit | App |
|---|---|---|---|
| Spoiler | `>!secret!<` | Hidden until tapped | ✅ |
| Superscript of one word | `^word` | Raised "word", up to the next space | ✅ |
| Superscript of several words | `^(two words)` | Raised "two words" | ✅ |
| Superscript of each word | `^and ^be ^reddited` | All three raised | ✅ |
| Superscript inside link text | `[^be ^reddited](https://…)` | A raised link | ✅ |
| Superscript towers | `^^double`, `2^2^2` | Stacked with no limit on old Reddit; not supported on new Reddit | ✅ stacked as on old Reddit, up to 3 levels, so a tower stays within its line |
| Subreddit link | `r/help` or `/r/help` | Link to the subreddit | ✅ |
| User link | `u/reddit` or `/u/reddit` | Link to the user | ✅ |
| Image uploaded to Reddit | `![img](id)` | The image, from `preview.redd.it` | ✅ as `https://preview.redd.it/<id>.jpeg` ❔ whether the extension is always right |
| Image by URL | `![img](https://…)` | The image | ✅ |
| Giphy GIF | `![gif](giphy\|id)` | The GIF | ✅ as `https://i.giphy.com/<id>/giphy.gif` |
| Smaller Giphy GIF | `![gif](giphy\|id\|downsized)` | The smaller GIF | ✅ as `giphy-downsized.gif` |
| Bare `preview.redd.it` image URL | `https://preview.redd.it/abc.png?width=640` | ❔ | Shown as an image |
| Subreddit emote | `![img](emote\|t5_…\|id)` | The emote | ❔ The app builds a `preview.redd.it` URL from the whole content, which is probably wrong |
| Other `![…](…)` | `![alt](https://…)` | Not an image: "!" and a link | ✅ |
| Images off (setting) | `![img](…)`, `![gif](…)` | — | Shown as `[img]` and `[gif]` links that open the image |

## Links

| Rule | Written | Reddit | App |
|---|---|---|---|
| Link | `[text](https://example.com)` | Link | ✅ |
| Bare `http://` and `https://` URLs are linked | `see https://www.reddit.com` | Link | ✅ (`http` is opened as `https`) |
| Other bare URLs aren't | `www.example.com` | Plain text | ✅ |
| Autolink in angle brackets | `<https://www.reddit.com/>` | Link without the brackets | ❌ #83: keeps `<` as text and puts `>` into the URL |
| Link title in quotes | `[text](https://example.com "title")` | Link to the URL; the title is a tooltip | ❌ #83: the title becomes part of the URL |
| Link title in parentheses (new Reddit only) | `[text](https://example.com (title))` | As above | ❌ #83: as above, plus a stray `)` |
| Reference link | `[text][id]` with `[id]: https://…` on its own line | Link; the definition isn't shown | ❌ #83: links to the text, and the definition is shown |
| Shortcut reference link | `[text]` with `[text]: https://…` | As above | ❌ #83: `[text]` is shown as typed, without a link, and the definition is shown with its URL linked |
| Schemes | `http://`, `https://`, `ftp://`, `mailto:`, `git://`, `steam://`, `irc://`, `ircs://`, `news://`, `mumble://`, `ssh://`, `ts3server://`, `/`, `#` | Link | ✅ for `mailto:`, `steam://` and `/`; ❔ the rest |
| No scheme | `[a](www.reddit.com)` | Not a working link | ❔ |

## Headings

| Rule | Written | Reddit | App |
|---|---|---|---|
| Six levels | `# H1` to `###### H6` | Headings | ✅ (level 6 is underlined in the app; Reddit italicizes it) |
| No space after `#` | `#hashtag` | ❔ Old Reddit makes a heading; new Reddit may not | Heading |
| Underlined level 1 | `Heading` ⏎ `===` | Level 1 heading | ❌ #83: becomes the paragraph "Heading ===" |
| Underlined level 2 | `Heading` ⏎ `---` | Level 2 heading | ❌ #83: a paragraph and a horizontal rule |

## Lists

| Rule | Written | Reddit | App |
|---|---|---|---|
| Bullets | `- item`, `* item` or `+ item` | Bulleted list | ✅ |
| Numbers with `.` | `1. item` | Numbered list | ✅ |
| Numbers with `)` (new Reddit only) | `1) item` | Numbered list | ✅ shown as `1.` |
| Numbering starts at 1 | `4) item` ⏎ `5) item` | ❔ The guide says lists must start with 1 | Keeps 4 and 5 |
| Bulleted sublist, indented 2 or more spaces | `- a` ⏎ `␣␣- b` | Nested list | ✅ |
| Numbered sublist, indented 3 or more spaces | `1. a` ⏎ `␣␣␣1. b` | Nested list | ✅ |
| Sublist indented 4 spaces | `- a` ⏎ `␣␣␣␣- b` | Nested list | ✅ |
| Paragraphs, quotes and other blocks inside a list item | Indented under the item | Part of the item | ❔ |

## Blockquotes

| Rule | Written | Reddit | App |
|---|---|---|---|
| Quote | `> text` | Quote | ✅ |
| Lazy continuation | `> one` ⏎ `two` | One quoted paragraph | ❌ #83: "two" is a separate, unquoted paragraph |
| Nested quote | `> > nested` | Quote in a quote | ✅ |
| Lists, headings and tables in a quote | `> - item`, `> ### Heading`, `> \|a\|b\|` … | Inside the quote | ✅ |

## Code

| Rule | Written | Reddit | App |
|---|---|---|---|
| Inline code | `` `code` `` | Code; no markdown inside | ✅ |
| Inline code with more backticks | ``` ``a ` b`` ``` | Code "a ` b" | ❌ #83: splits into the wrong pieces |
| Code block, indented 4 spaces | `␣␣␣␣code` | Code block | ✅ |
| Fenced with backticks (new Reddit only) | ```` ``` ```` ⏎ code ⏎ ```` ``` ```` | Code block | ✅ |
| Fenced with tildes (new Reddit only) | `~~~` ⏎ code ⏎ `~~~` | Code block | ✅ |
| Longer fence around a shorter one | ```` ```` ```` around ```` ``` ```` | Code block containing the inner fence | ✅ |

## Tables

| Rule | Written | Reddit | App |
|---|---|---|---|
| Table | `\| a \| b \|` ⏎ `\|---\|---\|` ⏎ `\| 1 \| 2 \|` | Table with a bold header row | ✅ |
| Without outer pipes | `a \| b` ⏎ `--\|--` ⏎ `1 \| 2` | Table | ✅ |
| Alignment | `:--` left, `--:` right, `:-:` centered | Aligned columns | ❌ #83: always left |
| Escaped pipe in a cell | `\| a \\\| b \|` | "a \| b" in one cell | ❌ #83: splits the cell |
| A row with fewer cells | `\| only \|` | Empty cells | ✅ |
| Images in cells | `![img](…)` in a cell | ❔ | Not shown |

## Horizontal rules

| Rule | Written | Reddit | App |
|---|---|---|---|
| Rule | `***`, `---` or `___` (three or more) | Horizontal line | ✅ |

## HTML entities

Reddit's API escapes `&`, `<` and `>` in bodies, so `RedditParser.decodeHtml` decodes the body before it's parsed. It replaces `&lt;`, `&gt;`, `&amp;`, `&quot;`, `&#39;` and `&nbsp;`, in that order, so an entity the writer typed reaches the parser as text, except `&quot;`, `&#39;` and `&nbsp;`: replacing `&amp;` first turns the API's `&amp;quot;` back into `&quot;`, which is then decoded too.

| Rule | Written | Reddit | App |
|---|---|---|---|
| Named entities | `&mdash;`, `&hearts;` | — ♥ (new Reddit decodes them) | ❌ #83: shown as typed, except `&quot;`, which is decoded |
| Numeric entities | `&#x2005;`, `&#8203;` | The character | ❌ #83: shown as typed, except `&#39;`, which is decoded |
| Non-breaking space | `&nbsp;` | A space that doesn't break | ❌ #83: decoded to a normal space, so lines can break there and a line of only `&nbsp;` counts as blank |
| `<`, `>` and `&` typed as text | `5 > 3 & 2 < 4` | As typed | ✅ |
| `&lt;`, `&gt;` and `&amp;` typed as entities | `&lt;` | `<` | ❌ #83: shown as typed, since they're replaced before `&amp;` |
