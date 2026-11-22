package com.tvbox.iptv

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class BrowserActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var cursor: ImageView
    private lateinit var fsContainer: FrameLayout
    private lateinit var tbAds: TextView

    private var cx = 0f
    private var cy = 0f
    private var blockAds = true
    private var pageHost: String? = null
    private var pageLoaded = false
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var chrome: WebChromeClient
    private val handler = Handler(Looper.getMainLooper())

    private val cosmeticJs = "(function(){try{var s=document.createElement('style');" +
        "s.textContent='ins.adsbygoogle,[id^=\"google_ads\"],[id^=\"div-gpt-ad\"]," +
        "iframe[src*=\"doubleclick.net\"],iframe[src*=\"googlesyndication.com\"]" +
        "{display:none!important}';document.head.appendChild(s);}catch(e){}})();"

    private fun dp(v: Int): Float = v * resources.displayMetrics.density

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_browser)
        hideSystemUi()

        root = findViewById(R.id.root)
        web = findViewById(R.id.web)
        cursor = findViewById(R.id.cursor)
        fsContainer = findViewById(R.id.fsContainer)
        tbAds = findViewById(R.id.tbAds)
        AdBlock.load(this)

        findViewById<View>(R.id.tbBack).setOnClickListener { if (web.canGoBack()) web.goBack() }
        findViewById<View>(R.id.tbReload).setOnClickListener { web.reload() }
        findViewById<View>(R.id.tbUp).setOnClickListener { scrollPage(-1) }
        findViewById<View>(R.id.tbDown).setOnClickListener { scrollPage(1) }
        findViewById<View>(R.id.tbExit).setOnClickListener { finish() }
        tbAds.setOnClickListener {
            blockAds = !blockAds
            tbAds.text = if (blockAds) "Anuncios: bloqueados" else "Anuncios: permitidos"
            web.reload()
        }

        setupWeb()
        root.post {
            cx = root.width / 2f
            cy = root.height / 2f
            updateCursor()
        }
        web.loadUrl(intent.getStringExtra("url") ?: "about:blank")
    }

    // ---------- Navegador ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setSupportMultipleWindows(true)
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.useWideViewPort = true
        s.loadWithOverviewMode = true

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (blockAds && AdBlock.isBlocked(request.url.host, pageHost)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val scheme = u.scheme ?: ""
                if (scheme != "http" && scheme != "https") return true
                if (blockAds && Build.VERSION.SDK_INT >= 24 && pageLoaded && request.isForMainFrame &&
                    !request.hasGesture() && pageHost != null && u.host != null &&
                    AdBlock.rootOf(u.host!!.lowercase()) != AdBlock.rootOf(pageHost!!.lowercase())
                ) return true
                return false
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                pageLoaded = false
                pageHost = Uri.parse(url ?: "").host
            }

            override fun onPageFinished(view: WebView, url: String?) {
                pageLoaded = true
                if (blockAds) view.evaluateJavascript(cosmeticJs, null)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                Toast.makeText(
                    this@BrowserActivity,
                    "Error de seguridad en la página. Revisa la fecha de la TV box.",
                    Toast.LENGTH_LONG
                ).show()
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                if (failingUrl != null && failingUrl == view?.url) {
                    Toast.makeText(this@BrowserActivity, "No se pudo abrir la página", Toast.LENGTH_LONG).show()
                }
            }
        }

        chrome = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                if (blockAds || resultMsg == null) return false
                val tmp = WebView(this@BrowserActivity)
                tmp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        web.loadUrl(r.url.toString())
                        return true
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = tmp
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                fsContainer.addView(
                    view,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                fsContainer.visibility = View.VISIBLE
            }

            override fun onHideCustomView() {
                customView?.let { fsContainer.removeView(it) }
                customView = null
                fsContainer.visibility = View.GONE
                customCallback?.onCustomViewHidden()
                customCallback = null
            }
        }
        web.webChromeClient = chrome
    }

    // ---------- Cursor con el control remoto ----------

    private fun updateCursor() {
        cursor.translationX = cx - dp(14)
        cursor.translationY = cy - dp(14)
    }

    private fun moveCursor(code: Int, repeat: Int) {
        val step = dp(10) * (1f + minOf(repeat, 30) / 5f)
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> cy -= step
            KeyEvent.KEYCODE_DPAD_DOWN -> cy += step
            KeyEvent.KEYCODE_DPAD_LEFT -> cx -= step
            KeyEvent.KEYCODE_DPAD_RIGHT -> cx += step
        }
        val w = root.width.toFloat()
        val h = root.height.toFloat()
        if (code == KeyEvent.KEYCODE_DPAD_DOWN && cy > h - dp(6) && customView == null) {
            web.scrollBy(0, dp(120).toInt())
        }
        cx = cx.coerceIn(0f, w - 1f)
        cy = cy.coerceIn(0f, h - 1f)
        updateCursor()
    }

    private fun clickAtCursor() {
        val t = SystemClock.uptimeMillis()
        val x = cx
        val y = cy
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        root.dispatchTouchEvent(down)
        down.recycle()
        handler.postDelayed({
            val up = MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            root.dispatchTouchEvent(up)
            up.recycle()
        }, 60)
    }

    private fun scrollPage(dir: Int) {
        web.scrollBy(0, dir * (web.height * 0.8f).toInt())
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down) moveCursor(e.keyCode, e.repeatCount)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down && e.repeatCount == 0) clickAtCursor()
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (down) scrollPage(-1)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (down) scrollPage(1)
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            customView != null -> chrome.onHideCustomView()
            web.canGoBack() -> web.goBack()
            else -> super.onBackPressed()
        }
    }

    // ---------- Ciclo de vida ----------

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}
