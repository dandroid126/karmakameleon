package com.reader.shared.data.api

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

actual fun createHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
                retryOnConnectionFailure(true)
            }
        }
    }
}
