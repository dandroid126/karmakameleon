package com.karmakameleon.shared.data.api

class RedditApiException(
    val errorCode: String,
    message: String,
) : Exception(message)
