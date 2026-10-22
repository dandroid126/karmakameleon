package com.karmakameleon.shared.data.api

import com.karmakameleon.shared.data.api.dto.ListingData
import com.karmakameleon.shared.data.api.dto.RedditResponse
import com.karmakameleon.shared.testing.readFixture
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * Helpers for the RedditParser tests, which parse responses saved from Reddit (in `sharedTesting/fixtures`) the way
 * [RedditApi] does. The responses were fetched with `raw_json=1`, like the app's requests, and the usernames in them
 * replaced. `message_listing.json` is written by hand in the same shape, since an inbox can't be fetched without logging
 * in.
 */

internal fun listing(fixture: String): ListingData =
    RedditParser.json.decodeFromString<RedditResponse<ListingData>>(readFixture(fixture)).data

/** The data of the thing with this ID in a listing fixture. */
internal fun thing(
    fixture: String,
    id: String,
): JsonObject =
    listing(fixture)
        .children
        .first {
            it.data.jsonObject["id"]
                ?.jsonPrimitive
                ?.content == id
        }.data.jsonObject

internal fun JsonObject.with(vararg fields: Pair<String, JsonElement>): JsonObject = JsonObject(this + fields)

internal fun JsonObject.without(key: String): JsonObject = JsonObject(this - key)

/** The post and its comments, as returned by /r/{subreddit}/comments/{id}. */
internal fun postWithComments(): List<RedditResponse<ListingData>> =
    RedditParser.json.decodeFromString<List<RedditResponse<ListingData>>>(readFixture("post_with_comments.json"))
