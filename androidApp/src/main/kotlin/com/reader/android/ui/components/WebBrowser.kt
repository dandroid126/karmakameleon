package com.reader.android.ui.components

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.reader.android.ui.menu.GlobalMenuManager
import com.reader.android.ui.menu.OverflowMenuItem
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun WebBrowserScreen(
    url: String,
    onBackClick: () -> Unit,
    globalMenuManager: GlobalMenuManager = koinInject()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var webViewRef: WebView? by remember { mutableStateOf(null) }

    DisposableEffect(url) {
        globalMenuManager.setScreenItems(listOf(
            OverflowMenuItem(
                title = "Copy Link",
                icon = Icons.Default.Link,
                onClick = {
                    coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", url))) }
                }
            ),
            OverflowMenuItem(
                title = "Open in Browser",
                icon = Icons.Default.OpenInBrowser,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            )
        ))
        onDispose { globalMenuManager.clearScreenItems() }
    }

    Scaffold(
        topBar = {
            UniversalTopAppBar(
                title = { Text(url.take(50)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                isLoading = true
                                canGoBack = view?.canGoBack() ?: false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                            }
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                        }
                        val httpsUrl = if (url.startsWith("http://")) {
                            url.replace("http://", "https://")
                        } else {
                            url
                        }
                        loadUrl(httpsUrl)
                        webViewRef = this
                    }
                },
                onRelease = { webView ->
                    webView.stopLoading()
                    webView.destroy()
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .align(androidx.compose.ui.Alignment.Center)
                )
            }
        }
    }
}
