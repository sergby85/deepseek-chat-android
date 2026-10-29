package com.deepseek.chat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 1001
    private var popupWebView: WebView? = null

    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true)
            userAgentString = desktopUserAgent
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return if (url.contains("deepseek.com") || url.contains("deepseek.ai")) {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    true
                }
            }

            // Strip X-Requested-With header for Google OAuth (WebView detection)
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                val isGoogleOAuth = url.contains("accounts.google.com") ||
                    url.contains("oauth2") ||
                    url.contains("google.com/o/oauth2")
                if (!isGoogleOAuth || request?.method != "GET") return null

                return try {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", desktopUserAgent)
                        // Copy original headers except X-Requested-With
                        request?.requestHeaders?.forEach { (k, v) ->
                            if (!k.equals("X-Requested-With", ignoreCase = true)) {
                                setRequestProperty(k, v)
                            }
                        }
                        // Sync cookies from CookieManager
                        val cookies = CookieManager.getInstance().getCookie(url)
                        if (!cookies.isNullOrEmpty()) setRequestProperty("Cookie", cookies)
                    }
                    // Follow redirects manually, syncing cookies
                    var current = conn
                    var hops = 0
                    while (hops < 10 && current.responseCode in 300..399) {
                        val loc = current.getHeaderField("Location") ?: break
                        current.disconnect()
                        val nextUrl = if (loc.startsWith("http")) loc else URL(URL(url), loc).toString()
                        current = (URL(nextUrl).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            instanceFollowRedirects = false
                            setRequestProperty("User-Agent", desktopUserAgent)
                            val c = CookieManager.getInstance().getCookie(nextUrl)
                            if (!c.isNullOrEmpty()) setRequestProperty("Cookie", c)
                        }
                        hops++
                    }
                    // Apply Set-Cookie to CookieManager
                    val setCookies = current.headerFields["Set-Cookie"]
                    setCookies?.forEach { c ->
                        CookieManager.getInstance().setCookie(current.url.toString(), c)
                    }
                    CookieManager.getInstance().flush()

                    val headers = mutableMapOf<String, String>()
                    current.headerFields.forEach { (k, v) ->
                        if (k != null && v != null && k.lowercase() !in setOf("set-cookie", "content-length", "transfer-encoding")) {
                            headers[k] = v.joinToString(", ")
                        }
                    }
                    WebResourceResponse(
                        current.contentType?.substringBefore(";")?.trim() ?: "text/html",
                        current.contentEncoding ?: "UTF-8",
                        current.responseCode,
                        current.responseMessage ?: "OK",
                        headers,
                        current.inputStream
                    )
                } catch (e: Exception) {
                    null
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(Intent.createChooser(intent, "Выберите файл"), fileChooserRequestCode)
                return true
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                val newWebView = WebView(this@MainActivity).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = desktopUserAgent
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                            val u = r?.url?.toString() ?: return false
                            if (u.contains("deepseek.com") || u.contains("deepseek.ai")) {
                                this@MainActivity.webView.loadUrl(u)
                                (parent as? android.view.ViewGroup)?.removeView(this@apply)
                                popupWebView = null
                                return true
                            }
                            return false
                        }
                    }
                }
                popupWebView = newWebView
                val container = findViewById<android.widget.FrameLayout>(R.id.webViewContainer)
                container.addView(newWebView, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ))
                newWebView.webChromeClient = this
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newWebView
                resultMsg?.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                val container = findViewById<android.widget.FrameLayout>(R.id.webViewContainer)
                popupWebView?.let { container.removeView(it) }
                popupWebView = null
            }
        }

        webView.loadUrl("https://chat.deepseek.com")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val target = popupWebView ?: webView
                if (target.canGoBack()) {
                    target.goBack()
                } else if (popupWebView != null) {
                    findViewById<android.widget.FrameLayout>(R.id.webViewContainer).removeView(popupWebView)
                    popupWebView = null
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileChooserRequestCode) {
            val results = if (resultCode == Activity.RESULT_OK) {
                data?.clipData?.let { clip ->
                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                } ?: data?.data?.let { arrayOf(it) }
            } else null
            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
        webView.onPause()
        popupWebView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        popupWebView?.onResume()
    }

    override fun onDestroy() {
        popupWebView?.destroy()
        webView.destroy()
        super.onDestroy()
    }
}