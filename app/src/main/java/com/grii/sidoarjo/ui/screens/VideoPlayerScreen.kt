package com.grii.sidoarjo.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Extracts a YouTube video ID from any YouTube URL format */
private fun extractYoutubeVideoId(url: String): String? {
    val trimmed = url.trim()
    val shortMatch = Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(trimmed)
    if (shortMatch != null) return shortMatch.groupValues[1]
    val longMatch = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(trimmed)
    if (longMatch != null) return longMatch.groupValues[1]
    val embedMatch = Regex("/embed/([A-Za-z0-9_-]{11})").find(trimmed)
    if (embedMatch != null) return embedMatch.groupValues[1]
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    title: String,
    rawUrl: String,
    year: String = "",
    description: String = "",
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val liveUrl = remember(rawUrl) { rawUrl.trim() }
    val youtubeVideoId = remember(liveUrl) { extractYoutubeVideoId(liveUrl) }
    var isLoading by remember(liveUrl) { mutableStateOf(liveUrl.isNotBlank()) }
    var errorMessage by remember(liveUrl) { mutableStateOf<String?>(null) }

    // Restore system bars when leaving
    DisposableEffect(Unit) {
        onDispose {
            val w = activity?.window ?: return@onDispose
            WindowCompat.getInsetsController(w, w.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(liveUrl) {
        errorMessage = if (liveUrl.isBlank()) "Video link is not available." else null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, fontWeight = FontWeight.Bold)
                        if (year.isNotBlank()) {
                            Text(
                                text = year,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                if (liveUrl.isNotBlank()) {
                    YoutubeWebViewContent(
                        liveUrl = liveUrl,
                        youtubeVideoId = youtubeVideoId,
                        onLoadingChange = { isLoading = it },
                        onError = { errorMessage = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isLoading && errorMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xF01A2A70))
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    errorMessage ?: "",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Button(
                                    onClick = { openExternalUrl(context, liveUrl) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    enabled = liveUrl.isNotBlank()
                                ) {
                                    Text("Open in Browser")
                                }
                            }
                        }
                    }
                }
            }

            // Description + open link section (portrait)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (liveUrl.isNotBlank()) {
                    Button(
                        onClick = { openExternalUrl(context, liveUrl) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Open in YouTube")
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun YoutubeWebViewContent(
    liveUrl: String,
    youtubeVideoId: String?,
    onLoadingChange: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (liveUrl.isBlank()) return

    // Target URL: YouTube mobile page (works for all videos, no embed restrictions)
    val targetUrl = if (youtubeVideoId != null)
        "https://m.youtube.com/watch?v=$youtubeVideoId"
    else
        liveUrl

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            WebView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // Mobile Chrome UA so YouTube serves correct mobile player
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        if (url.isBlank()) return false
                        val scheme = Uri.parse(url).scheme.orEmpty().lowercase()
                        if (scheme.isNotBlank() && scheme != "http" && scheme != "https") {
                            openExternalUrl(viewContext, url)
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onLoadingChange(false)
                    }

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: android.graphics.Bitmap?
                    ) {
                        onLoadingChange(true)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) {
                            onError("Failed to open video.")
                            onLoadingChange(false)
                        }
                    }
                }

                // Custom WebChromeClient: handle YouTube's fullscreen button properly
                var fullscreenView: View? = null
                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        fullscreenView = view
                        val decorView = (viewContext as? Activity)?.window?.decorView as? ViewGroup
                        decorView?.addView(
                            view,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        val window = (viewContext as? Activity)?.window
                        if (window != null) {
                            WindowCompat.getInsetsController(window, window.decorView).apply {
                                hide(WindowInsetsCompat.Type.systemBars())
                                systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            }
                        }
                    }

                    override fun onHideCustomView() {
                        val decorView = (viewContext as? Activity)?.window?.decorView as? ViewGroup
                        fullscreenView?.let { decorView?.removeView(it) }
                        fullscreenView = null
                        val window = (viewContext as? Activity)?.window
                        if (window != null) {
                            WindowCompat.getInsetsController(window, window.decorView)
                                .show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                }

                loadUrl(targetUrl)
            }
        },
        update = { /* no-op: URL set in factory */ }
    )
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
