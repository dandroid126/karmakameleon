package com.karmakameleon.android.ui.profile

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Shows Reddit's authorization page. Login happens in-app so that any redirect URI works: [onRedirect]
 * is asked about every page before it loads, and returns true for the redirect back to the app. JavaScript is
 * on because Reddit's login page needs it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RedditLoginWebView(
    authUrl: String,
    onRedirect: (url: String) -> Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnRedirect by rememberUpdatedState(onRedirect)
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef: WebView? by remember { mutableStateOf(null) }

    BackHandler {
        val webView = webViewRef
        if (webView != null && webView.canGoBack()) webView.goBack() else onCancel()
    }

    Box(modifier = modifier.fillMaxSize().imePadding()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient =
                        object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = currentOnRedirect(request.url.toString())

                            override fun onPageStarted(
                                view: WebView,
                                url: String,
                                favicon: Bitmap?,
                            ) {
                                // Backstop for navigations that don't go through shouldOverrideUrlLoading.
                                if (currentOnRedirect(url)) {
                                    view.stopLoading()
                                    return
                                }
                                isLoading = true
                            }

                            override fun onPageFinished(
                                view: WebView,
                                url: String,
                            ) {
                                isLoading = false
                            }
                        }
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        // Reddit's login rejects the WebView user agent ("Something went wrong logging in"),
                        // so present the same Chrome version without the WebView markers.
                        userAgentString =
                            WebSettings
                                .getDefaultUserAgent(context)
                                .replace("; wv)", ")")
                                .replace(" Version/4.0", "")
                    }
                    loadUrl(authUrl)
                    webViewRef = this
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .size(48.dp)
                        .align(Alignment.Center),
            )
        }
    }
}

/** Signs the login WebView out of Reddit, so the next sign in can use a different account. */
fun clearRedditLoginSession() {
    CookieManager.getInstance().apply {
        removeAllCookies(null)
        flush()
    }
    WebStorage.getInstance().deleteAllData()
}
