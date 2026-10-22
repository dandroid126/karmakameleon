package com.karmakameleon.android.ui.profile

import android.os.Looper
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.testing.readFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.days

// The logged in user: the author of the comment in user_comments.json.
const val OWN_USERNAME = "user37"

/**
 * user_about.json for [username], who joined two years ago, so screenshots show the same age whenever they run. The
 * user is a moderator with Reddit Premium.
 */
fun userAbout(username: String): JsonObject {
    val about = Json.parseToJsonElement(readFixture("user_about.json")).jsonObject
    val created = System.currentTimeMillis() / 1000 - (2 * 365).days.inWholeSeconds
    val data = about.getValue("data").jsonObject + mapOf("name" to JsonPrimitive(username), "created_utc" to JsonPrimitive(created))
    return JsonObject(about + ("data" to JsonObject(data)))
}

/**
 * Loads the logged in account, as the app does when it starts, so a profile screen knows which profile is its own.
 * Requests run on the main thread, so this runs it until they're answered.
 */
fun loadOwnAccount() {
    val userRepository = GlobalContext.get().get<UserRepository>()
    CoroutineScope(Dispatchers.Main).launch { userRepository.loadCurrentUser() }
    shadowOf(Looper.getMainLooper()).idle()
}
