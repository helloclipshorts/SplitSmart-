package com.splitsmart.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Status bar color — DO NOT do edge-to-edge, system handles insets correctly
        window.statusBarColor = android.graphics.Color.parseColor("#0A0A0F")
        window.navigationBarColor = android.graphics.Color.parseColor("#0A0A0F")

        WebView.setWebContentsDebuggingEnabled(true)
        webView = WebView(this)
        setContentView(webView)
        setupWebView()
        webView.loadUrl("file:///android_asset/index.html")
    }

    @Suppress("DEPRECATION")
    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true                       // localStorage — CRITICAL
        s.databaseEnabled = true
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.allowFileAccessFromFileURLs = true             // CRITICAL for file:// pages
        s.allowUniversalAccessFromFileURLs = true        // CRITICAL for localStorage on file://
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.mediaPlaybackRequiresUserGesture = false

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.addJavascriptInterface(Bridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return when {
                    url.startsWith("upi://") -> {
                        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Pay via UPI")) }
                        catch (e: Exception) { Toast.makeText(this@MainActivity, "Install Google Pay or PhonePe", Toast.LENGTH_LONG).show() }
                        true
                    }
                    url.startsWith("intent://") -> {
                        try {
                            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                            if (packageManager.resolveActivity(intent, 0) != null) {
                                startActivity(intent)
                            } else {
                                val fallback = url.replace("intent://", "upi://").replace(Regex("#Intent.*"), "")
                                startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(fallback)), "Pay via UPI"))
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "No UPI app found", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    url.startsWith("https://wa.me") -> {
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        catch (e: Exception) { Toast.makeText(this@MainActivity, "WhatsApp not installed", Toast.LENGTH_SHORT).show() }
                        true
                    }
                    url.contains("fonts.googleapis.com") || url.contains("fonts.gstatic.com") -> false
                    url.startsWith("https://") -> {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        true
                    }
                    else -> false
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
                this@MainActivity.filePathCallback = filePathCallback
                return try { startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST); true }
                catch (e: Exception) { this@MainActivity.filePathCallback = null; false }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            filePathCallback?.onReceiveValue(if (resultCode == Activity.RESULT_OK && data?.data != null) arrayOf(data.data!!) else arrayOf())
            filePathCallback = null
        }
    }

    // FIXED: Back button calls JS first, exits app only if JS says nothing to go back to
    override fun onBackPressed() {
        webView.evaluateJavascript("window.androidBackPressed()") { result ->
            if (result?.trim('"') != "true") {
                super.onBackPressed()
            }
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun shareText(text: String, title: String) {
            runOnUiThread {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_SUBJECT, title)
                }, "Share via"))
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
        }
    }
}
