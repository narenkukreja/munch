package com.munch.reddit.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.munch.reddit.R
import com.munch.reddit.ui.theme.MunchForRedditTheme
import java.io.ByteArrayInputStream
import java.util.Locale

class WebViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val initialTitle = intent.getStringExtra(EXTRA_TITLE)
        if (targetUrl.isBlank()) {
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            MunchForRedditTheme {
                val view = LocalView.current
                val window = this@WebViewActivity.window
                SideEffect {
                    WindowInsetsControllerCompat(window, view).apply {
                        isAppearanceLightStatusBars = false
                        isAppearanceLightNavigationBars = false
                    }
                    window.statusBarColor = Color.Transparent.toArgb()
                    window.navigationBarColor = Color.Transparent.toArgb()
                }

                BrowserScreen(
                    url = targetUrl,
                    initialTitle = initialTitle,
                    onClose = {
                        finish()
                        overridePendingTransition(
                            R.anim.slide_in_left,
                            R.anim.slide_out_right
                        )
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_URL = "BROWSER_URL"
        const val EXTRA_TITLE = "BROWSER_TITLE"

        fun buildIntent(context: Context, url: String, title: String? = null): Intent =
            Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                if (!title.isNullOrBlank()) {
                    putExtra(EXTRA_TITLE, title)
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BrowserScreen(
    url: String,
    initialTitle: String?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val titleFallback = remember(url, initialTitle) {
        when {
            !initialTitle.isNullOrBlank() -> initialTitle
            else -> runCatching { Uri.parse(url).host }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: url
        }
    }
    var pageTitle by remember(url, initialTitle) { mutableStateOf(titleFallback) }
    var progress by remember { mutableFloatStateOf(0f) }
    val adBlocker = remember { SimpleAdBlocker() }
    var webViewState by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewState?.let { view ->
                view.stopLoading()
                view.destroy()
            }
            webViewState = null
        }
    }

    val closeRequest by rememberUpdatedState(onClose)
    BackHandler {
        val current = webViewState
        if (current?.canGoBack() == true) {
            current.goBack()
        } else {
            closeRequest()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = pageTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                val current = webViewState
                                if (current?.canGoBack() == true) {
                                    current.goBack()
                                } else {
                                    closeRequest()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                if (progress < 0.999f) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFFC107),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    webViewState = this
                    applyBrowserSettings()
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            if (!title.isNullOrBlank()) {
                                pageTitle = title
                            }
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress.coerceIn(0, 100) / 100f
                        }
                    }
                    webViewClient = BrowserWebViewClient(
                        adBlocker = adBlocker,
                        onFallbackTitle = { fallback ->
                            if (!fallback.isNullOrBlank()) {
                                pageTitle = fallback
                            }
                        }
                    )
                    loadUrl(url)
                }
            },
            update = { view ->
                webViewState = view
            }
        )
    }
}

private class BrowserWebViewClient(
    private val adBlocker: SimpleAdBlocker,
    private val onFallbackTitle: (String?) -> Unit
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val uri = request?.url ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme == "http" || scheme == "https") {
            return false
        }

        val context = view?.context ?: return true
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching { context.startActivity(intent) }
        return true
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onFallbackTitle(view?.title)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val targetUrl = request?.url?.toString()
        return if (adBlocker.shouldBlock(targetUrl)) {
            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        } else {
            super.shouldInterceptRequest(view, request)
        }
    }
}

private class SimpleAdBlocker {
    private val blockedKeywords = listOf(
        "doubleclick",
        "googlesyndication",
        "googletagservices",
        "googletagmanager",
        "adservice",
        "adsystem",
        "adnxs",
        "taboola",
        "outbrain",
        "moatads",
        "scorecardresearch",
        "zedo",
        "pubmatic",
        "adform",
        "advertising",
        "appsflyer"
    )

    fun shouldBlock(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return false
        return blockedKeywords.any { keyword ->
            host.contains(keyword, ignoreCase = true)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.applyBrowserSettings() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.loadsImagesAutomatically = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.mediaPlaybackRequiresUserGesture = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        settings.safeBrowsingEnabled = true
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        settings.forceDark = WebSettings.FORCE_DARK_AUTO
    }

    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@applyBrowserSettings, false)
    }

    scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
    isVerticalScrollBarEnabled = false
    overScrollMode = WebView.OVER_SCROLL_NEVER
}
