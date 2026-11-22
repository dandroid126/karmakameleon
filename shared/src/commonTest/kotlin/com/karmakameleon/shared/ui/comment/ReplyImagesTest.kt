package com.karmakameleon.shared.ui.comment

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
}
