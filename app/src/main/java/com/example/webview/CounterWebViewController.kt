package com.example.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

interface CounterBridgeCallbacks {
    fun onPageProgress(progress: Int)
    fun onPageStarted(url: String)
    fun onPageFinished(url: String)
    fun onPageError(description: String)
    fun onCounterPayload(payload: DomScanPayload)
    fun onElementPicked(picked: PickedElementPayload)
}

class CounterWebViewController(
    context: Context,
    private val callbacks: CounterBridgeCallbacks
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pinnedSelector: String = ""
    private var pickerEnabled: Boolean = false
    private var lastLoadedUrl: String = ""
    private var isDestroyed: Boolean = false
    private var rendererRecoveryAttempts: Int = 0

    private val _webViewFlow = MutableStateFlow(createWebView())
    val webViewFlow: StateFlow<WebView> = _webViewFlow.asStateFlow()

    val webView: WebView
        get() = _webViewFlow.value

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        return WebView(appContext).apply {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = settings.userAgentString.replace("; wv", "")
            }

            addJavascriptInterface(JsBridge(), "CounterBridge")

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    callbacks.onPageProgress(newProgress)
                    if (newProgress >= 95) {
                        injectDomObserver(view)
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (!url.isNullOrBlank()) {
                        lastLoadedUrl = url
                        callbacks.onPageStarted(url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    rendererRecoveryAttempts = 0
                    injectDomObserver(view)
                    setPickerMode(pickerEnabled)
                    if (!url.isNullOrBlank()) {
                        callbacks.onPageFinished(url)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        callbacks.onPageError(error?.description?.toString() ?: "Load error")
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    handleRendererCrash(view)
                    return true
                }
            }
        }
    }

    private fun handleRendererCrash(crashedView: WebView?) {
        if (isDestroyed) return
        mainHandler.post {
            if (isDestroyed) return@post
            crashedView?.let { wv ->
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.removeJavascriptInterface("CounterBridge")
                wv.destroy()
            }

            if (rendererRecoveryAttempts < 2) {
                rendererRecoveryAttempts++
                val fresh = createWebView()
                _webViewFlow.value = fresh
                if (lastLoadedUrl.isNotBlank()) {
                    fresh.loadUrl(lastLoadedUrl)
                }
            } else {
                callbacks.onPageError("WebView renderer terminated")
            }
        }
    }

    fun loadUrlIfNeeded(url: String) {
        if (isDestroyed || url.isBlank()) return
        if (lastLoadedUrl != url) {
            lastLoadedUrl = url
            webView.loadUrl(url)
        }
    }

    fun forceLoadUrl(url: String) {
        if (isDestroyed || url.isBlank()) return
        lastLoadedUrl = url
        webView.loadUrl(url)
    }

    fun reload() {
        if (isDestroyed) return
        webView.reload()
    }

    fun updatePinnedSelector(selector: String) {
        if (isDestroyed || pinnedSelector == selector) return
        pinnedSelector = selector
        val quoted = JSONObject.quote(selector)
        mainHandler.post {
            if (!isDestroyed) {
                webView.evaluateJavascript(
                    "if (window.__redCounterSetPinned) window.__redCounterSetPinned($quoted);",
                    null
                )
            }
        }
    }

    fun setPickerMode(enabled: Boolean) {
        if (isDestroyed) return
        pickerEnabled = enabled
        mainHandler.post {
            if (!isDestroyed) {
                webView.evaluateJavascript(
                    "if (window.__redCounterSetPicker) window.__redCounterSetPicker($enabled);",
                    null
                )
            }
        }
    }

    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        mainHandler.post {
            val wv = _webViewFlow.value
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.removeJavascriptInterface("CounterBridge")
            wv.destroy()
        }
    }

    private fun injectDomObserver(targetView: WebView? = webView) {
        if (isDestroyed || targetView == null) return
        val script = CounterDomScript.buildInstallScript(pinnedSelector)
        targetView.evaluateJavascript(script, null)
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun onCounterUpdate(json: String) {
            if (isDestroyed) return
            val payload = CounterDomScript.parseDomPayload(json) ?: return
            mainHandler.post {
                if (!isDestroyed) {
                    callbacks.onCounterPayload(payload)
                }
            }
        }

        @JavascriptInterface
        fun onElementPicked(json: String) {
            if (isDestroyed) return
            val picked = CounterDomScript.parsePickedPayload(json) ?: return
            pickerEnabled = false
            pinnedSelector = picked.selector
            mainHandler.post {
                if (!isDestroyed) {
                    callbacks.onElementPicked(picked)
                }
            }
        }
    }
}
