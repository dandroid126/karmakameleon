package com.karmakameleon.shared.ui.comment

import com.karmakameleon.shared.domain.markdown.MarkdownInline
import com.karmakameleon.shared.util.markdown.parseInlineMarkdown
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplyImagesTest {
    @Test
    fun imageKeys_findsTokensWhateverTheirAltText() {
        assertEquals(setOf("img-1", "img-12"), "a ![image](img-1) b ![my cat](img-12) ![x](https://e.com/a.png)".imageKeys())
    }

    @Test
    fun withAssetIds_swapsUploadedTokensAndLeavesTheRest() {
        val text = "![image](img-1) ![image](img-2) ![x](https://e.com/a.png)"

        assertEquals("![img](asset1) ![image](img-2) ![x](https://e.com/a.png)", text.withAssetIds(mapOf("img-1" to "asset1")))
    }

    @Test
    fun replacingImageToken_onlyTouchesThatKey() {
        assertEquals("a  b ![image](img-2)", "a ![image](img-1) b ![image](img-2)".replacingImageToken("img-1"))
        assertEquals("[GIF](u)", "![gif](img-1)".replacingImageToken("img-1", "[GIF](u)"))
    }

    @Test
    fun withPreviewImages_showsTheGivenKeysAsImages() {
        val text = "![image](img-1) ![image](img-2) ![x](https://e.com/a.png)"

        val preview = text.withPreviewImages(setOf("img-1"))

        assertEquals("![img](${previewImageUrl("img-1")}) ![image](img-2) ![x](https://e.com/a.png)", preview)
        val image = parseInlineMarkdown(preview).first()
        assertEquals(MarkdownInline.Image(previewImageUrl("img-1")), image)
    }
}
