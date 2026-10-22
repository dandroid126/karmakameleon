package com.karmakameleon.android.ui.components

import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.test.FakeImageLoaderEngine
import com.karmakameleon.android.R
import com.karmakameleon.shared.domain.model.GalleryData
import com.karmakameleon.shared.domain.model.GalleryItem
import com.karmakameleon.shared.domain.model.ImageSource
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.Preview
import com.karmakameleon.shared.domain.model.PreviewImage
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import kotlin.time.Duration.Companion.hours

// Three hours old, so its age reads "3h" whenever the test runs.
val threeHoursAgo get() = System.currentTimeMillis() / 1000 - 3.hours.inWholeSeconds

// A text post. Tests change what they need with copy().
fun testPost(): Post {
    val created = threeHoursAgo
    return Post(
        id = "abc123",
        name = "t3_abc123",
        title = "What's your favorite Kotlin feature?",
        author = "kotlinfan",
        subreddit = "Kotlin",
        subredditId = "t5_kotlin",
        selfText = "Mine is sealed classes. They make when expressions exhaustive.",
        selfTextHtml = null,
        url = "https://www.reddit.com/r/Kotlin/comments/abc123/whats_your_favorite_kotlin_feature/",
        permalink = "/r/Kotlin/comments/abc123/whats_your_favorite_kotlin_feature/",
        thumbnail = "self",
        thumbnailWidth = null,
        thumbnailHeight = null,
        preview = null,
        score = 1_234,
        upvoteRatio = 0.97f,
        numComments = 56,
        created = created,
        createdUtc = created,
        isNsfw = false,
        isSpoiler = false,
        isStickied = false,
        isLocked = false,
        isArchived = false,
        isSaved = false,
        isHidden = false,
        likes = null,
        domain = "self.Kotlin",
        postHint = "self",
        linkFlairText = null,
        linkFlairBackgroundColor = null,
        linkFlairTextColor = null,
        authorFlairText = null,
        distinguished = null,
        media = null,
        galleryData = null,
        crosspostParent = null,
    )
}

private fun image(name: String) = ImageSource(url = "https://i.redd.it/$name.jpg", width = 640, height = 480)

fun testImagePost(): Post =
    testPost().copy(
        title = "My cat learned to open the fridge",
        selfText = null,
        url = "https://i.redd.it/cat.jpg",
        domain = "i.redd.it",
        postHint = "image",
        thumbnail = "https://i.redd.it/cat_thumb.jpg",
        preview = Preview(images = listOf(PreviewImage(source = image("cat"), resolutions = listOf(image("cat_216")))), enabled = true),
    )

fun testGalleryPost(): Post =
    testImagePost().copy(
        title = "Three photos from my hike",
        url = "https://www.reddit.com/gallery/abc123",
        postHint = null,
        galleryData =
            GalleryData(
                (1..3).map { GalleryItem(mediaId = "m$it", id = it.toLong(), caption = null, url = image("hike$it").url) },
            ),
    )

fun testLinkPost(): Post =
    testPost().copy(
        title = "Kotlin 2.4 released",
        selfText = null,
        url = "https://blog.jetbrains.com/kotlin/2026/kotlin-2-4/",
        domain = "blog.jetbrains.com",
        postHint = "link",
        thumbnail = "default",
    )

/**
 * Serves every image from the app's own Karma Kameleon drawable instead of the network, so screenshots always show the
 * same image. [requested] lists what the app asked for, usually its URLs.
 */
class FakeImagesRule : ExternalResource() {
    val requested = mutableListOf<Any>()

    @OptIn(DelicateCoilApi::class)
    override fun before() {
        val context = RuntimeEnvironment.getApplication()
        val image = checkNotNull(ContextCompat.getDrawable(context, R.drawable.karma_kameleon)).asImage()
        val engine =
            FakeImageLoaderEngine
                .Builder()
                // Records each request, then lets the default answer it.
                .intercept({ data ->
                    requested += data
                    false
                }, image)
                .default(image)
                .build()
        SingletonImageLoader.setUnsafe(
            ImageLoader
                .Builder(context)
                .components { add(engine) }
                .build(),
        )
    }

    @OptIn(DelicateCoilApi::class)
    override fun after() {
        SingletonImageLoader.reset()
    }
}
