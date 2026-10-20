package com.karmakameleon.android.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.karmakameleon.android.ui.menu.GlobalMenuManager
import com.karmakameleon.android.ui.menu.OverflowMenuItem
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun WebBrowserScreen(
    url: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    globalMenuManager: GlobalMenuManager = koinInject(),
) {
    var isLoading by remember { mutableStateOf(true) }
    var webView: WebView? by remember { mutableStateOf(null) }
    BrowserMenuItems(url = url, globalMenuManager = globalMenuManager)

    Scaffold(
        modifier = modifier,
        topBar = {
            UniversalTopAppBar(
                title = { Text(url.take(50)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
        ) {
            BrowserWebView(url = url, onLoadingChange = { isLoading = it }, onCreate = { webView = it })
            if (isLoading) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .align(androidx.compose.ui.Alignment.Center),
                )
            }
        }
    }
}

// Adds Copy Link and Open in Browser to the overflow menu while the page is shown.
@Composable
private fun BrowserMenuItems(
    url: String,
    globalMenuManager: GlobalMenuManager,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    DisposableEffect(url) {
        globalMenuManager.setScreenItems(
            listOf(
                OverflowMenuItem(
                    title = "Copy Link",
                    icon = Icons.Default.Link,
                    onClick = { coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", url))) } },
                ),
                OverflowMenuItem(
                    title = "Open in Browser",
                    icon = Icons.Default.OpenInBrowser,
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
                ),
            ),
        )
        onDispose { globalMenuManager.clearScreenItems() }
    }
}

// Most of the pages people open from posts need JavaScript to work. http links are upgraded to https.
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserWebView(
    url: String,
    onLoadingChange: (Boolean) -> Unit,
    onCreate: (WebView) -> Unit,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            onLoadingChange(true)
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            onLoadingChange(false)
                        }
                    }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    // Web pages don't need the device's files or content:// URIs, like the app's own FileProvider.
                    allowContentAccess = false
                    allowFileAccess = false
                }
                loadUrl(if (url.startsWith("http://")) url.replace("http://", "https://") else url)
                onCreate(this)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
        modifier = Modifier.fillMaxSize(),
    )
}
