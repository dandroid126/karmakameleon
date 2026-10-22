package com.karmakameleon.shared.di

import com.karmakameleon.shared.data.api.HttpClientFactory
import com.karmakameleon.shared.data.api.OkHttpClientFactory
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertSame

// The token settings need a Context, so the app's AppModulesTest checks them.
class PlatformModuleTest {
    @Test
    fun clientsUseOkHttp() {
        val app = koinApplication { modules(platformModule) }

        assertSame(OkHttpClientFactory, app.koin.get<HttpClientFactory>())
        app.close()
    }
}
