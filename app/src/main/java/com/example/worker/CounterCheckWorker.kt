package com.example.worker

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alerts.CounterAlertManager
import com.example.data.local.AppDatabase
import com.example.data.local.CounterReadingEntity
import com.example.data.local.ReadingEventType
import com.example.data.preferences.SettingsRepository
import com.example.webview.CounterDomScript
import com.example.webview.DomScanPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class CounterCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settingsRepo = SettingsRepository(applicationContext)
        val counterDao = AppDatabase.getInstance(applicationContext).counterDao()
        val alertManager = CounterAlertManager(applicationContext)

        val settings = settingsRepo.getSnapshot()
        val now = System.currentTimeMillis()

        val payload = fetchCounterViaHeadlessWebView(
            url = settings.targetUrl,
            pinnedSelector = settings.pinnedSelector
        )

        val primary = if (settings.pinnedSelector.isNotBlank()) {
            payload?.primary?.takeIf { it.kind != "TIMER" }
        } else {
            payload?.totalCounter ?: payload?.primary?.takeIf { it.kind != "TIMER" }
        }
        if (primary == null) {
            settingsRepo.updateBackgroundStatus(now, "تعذر قراءة العداد الإجمالي في الخلفية")
            return Result.success()
        }

        val prevNumeric = settings.lastKnownNumeric
        val prevRaw = settings.lastKnownRaw
        val newNumeric = primary.numericValue
        val newRaw = primary.value

        val increased = prevNumeric != null && newNumeric != null && newNumeric > prevNumeric
        val changed = prevRaw.isNotBlank() && prevRaw != newRaw
        val isInitial = prevRaw.isBlank()

        var alertReason: String? = null
        if (increased && settings.alertOnIncrease) {
            alertReason = "ارتفعت قيمة العداد الإجمالية من $prevRaw إلى $newRaw"
        } else if (settings.alertOnZero && newNumeric == 0L && prevNumeric != 0L) {
            alertReason = "وصل العداد إلى الصفر"
        } else if (settings.thresholdEnabled && newNumeric != null && newNumeric in 1..settings.thresholdValue) {
            alertReason = "اقترب العداد من الحد (${settings.thresholdValue})"
        } else if (changed && settings.alertOnChange) {
            alertReason = "تغيّر العداد من $prevRaw إلى $newRaw"
        }

        if (alertReason != null) {
            alertManager.triggerAlert(
                label = primary.label.ifBlank { "العداد الإجمالي - فودافون RED" },
                value = newRaw,
                reason = alertReason,
                playSound = settings.soundEnabled,
                vibrate = settings.vibrationEnabled,
                postNotification = settings.notificationsEnabled
            )
        }

        if (isInitial || changed || alertReason != null) {
            val eventType = when {
                alertReason != null -> ReadingEventType.ALERT
                isInitial -> ReadingEventType.INITIAL
                else -> ReadingEventType.CHANGED
            }
            counterDao.insertReading(
                CounterReadingEntity(
                    url = settings.targetUrl,
                    selector = primary.selector,
                    label = primary.label.ifBlank { "العداد الإجمالي (خلفية)" },
                    rawValue = newRaw,
                    numericValue = newNumeric,
                    previousValue = if (isInitial) null else prevRaw,
                    eventType = eventType.name,
                    timestamp = now
                )
            )
        }

        settingsRepo.updateLastKnownReading(raw = newRaw, numeric = newNumeric)
        val statusSummary = if (increased) {
            "ارتفع الإجمالي: $prevRaw ← $newRaw"
        } else {
            "الإجمالي الحالي: $newRaw"
        }
        settingsRepo.updateBackgroundStatus(now, statusSummary)

        return Result.success()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchCounterViaHeadlessWebView(
        url: String,
        pinnedSelector: String
    ): DomScanPayload? = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        val mainHandler = Handler(Looper.getMainLooper())
        try {
            withTimeoutOrNull(25_000L) {
                suspendCancellableCoroutine { cont ->
                    var settled = false
                    var latestPayload: DomScanPayload? = null
                    var pollRunnable: Runnable? = null

                    fun finish(result: DomScanPayload?) {
                        if (settled) return
                        settled = true
                        pollRunnable?.let { mainHandler.removeCallbacks(it) }
                        if (cont.isActive) {
                            cont.resume(result)
                        }
                    }

                    val wv = WebView(applicationContext)
                    webView = wv
                    wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

                    wv.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = userAgentString.replace("; wv", "")
                    }

                    val bridge = object {
                        @JavascriptInterface
                        fun onCounterUpdate(json: String) {
                            val parsed = CounterDomScript.parseDomPayload(json) ?: return
                            mainHandler.post {
                                latestPayload = parsed
                                val target = parsed.totalCounter ?: parsed.primary?.takeIf { it.kind != "TIMER" }
                                if (target != null) {
                                    finish(parsed)
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onElementPicked(json: String) = Unit
                    }

                    wv.addJavascriptInterface(bridge, "CounterBridge")

                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            super.onPageFinished(view, loadedUrl)
                            val script = CounterDomScript.buildInstallScript(pinnedSelector)
                            var attempts = 0
                            val runner = object : Runnable {
                                override fun run() {
                                    if (settled) return
                                    wv.evaluateJavascript(script, null)
                                    attempts++
                                    if (attempts < 6) {
                                        mainHandler.postDelayed(this, 850L)
                                    } else {
                                        finish(latestPayload)
                                    }
                                }
                            }
                            pollRunnable = runner
                            mainHandler.postDelayed(runner, 600L)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                finish(null)
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?
                        ): Boolean {
                            finish(latestPayload)
                            return true
                        }
                    }

                    cont.invokeOnCancellation {
                        pollRunnable?.let { mainHandler.removeCallbacks(it) }
                    }

                    wv.loadUrl(url)
                }
            }
        } finally {
            webView?.let { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.removeJavascriptInterface("CounterBridge")
                wv.destroy()
            }
        }
    }
}
