package com.karmakameleon.konsist

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Checks that the patterns in Rules find what they're meant to, and nothing else, so a rule can't pass by never
// matching.
class RulesTest {
    @Test
    fun hardcodedColor_matchesLiteralAndNamedColors() {
        listOf(
            "Color(0xFF6200EE)",
            "Color(red = 0.5f, green = 0f, blue = 0f)",
            "Color(255, 0, 0)",
            "Color.Black",
            "color = Color.White,",
            "android.graphics.Color.parseColor(\"#FFFFFF\")",
            "Color.hsv(120f, 1f, 1f)",
        ).forEach { assertTrue(Rules.hardcodedColor.containsMatchIn(it), it) }
    }

    @Test
    fun hardcodedColor_allowsComputedAndThemeColors() {
        listOf(
            "Color(argb)",
            "Color.Transparent",
            "Color.Unspecified",
            "textColor.copy(alpha = 0.5f)",
            "MaterialTheme.colorScheme.primary",
            "import androidx.compose.ui.graphics.Color",
            "fun tint(color: Color): Color",
        ).forEach { assertFalse(Rules.hardcodedColor.containsMatchIn(it), it) }
    }

    @Test
    fun platformReference_matchesPlatformPackages() {
        listOf(
            "okhttp3.OkHttpClient.Builder()",
            "import android.content.Context",
            "java.io.File(path)",
            "import io.ktor.client.engine.okhttp.OkHttp",
        ).forEach { assertTrue(Rules.platformReference.containsMatchIn(it), it) }
    }

    @Test
    fun platformReference_allowsMultiplatformPackages() {
        listOf(
            "import androidx.lifecycle.ViewModel",
            "import kotlinx.coroutines.flow.Flow",
            "import io.ktor.client.HttpClient",
            "import com.karmakameleon.shared.data.api.RedditApi",
        ).forEach { assertFalse(Rules.platformReference.containsMatchIn(it), it) }
    }

    @Test
    fun apiClientUse_matchesTheApiClientAndKtor() {
        listOf(
            "import com.karmakameleon.shared.data.api.RedditApi",
            "redditApi: RedditApi = koinInject()",
            "import io.ktor.client.request.get",
        ).forEach { assertTrue(Rules.apiClientUse.containsMatchIn(it), it) }
    }

    @Test
    fun apiClientUse_allowsRepositoriesAndApiModels() {
        listOf(
            "postRepository.resolveShareLink(subreddit, shareId)",
            "import com.karmakameleon.shared.data.api.CommentOrMore",
            "is RedditApiException ->",
        ).forEach { assertFalse(Rules.apiClientUse.containsMatchIn(it), it) }
    }

    @Test
    fun runCatchingCall_matchesOnlyRunCatching() {
        assertTrue(Rules.runCatchingCall.containsMatchIn("runCatching { load() }"))
        assertTrue(Rules.runCatchingCall.containsMatchIn("value.runCatching { parse() }"))
        assertFalse(Rules.runCatchingCall.containsMatchIn("runSuspendCatching { load() }"))
    }
}
