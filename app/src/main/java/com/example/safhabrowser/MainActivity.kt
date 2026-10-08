package com.example.safhabrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.webkit.CookieManager
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

private const val GOOGLE_HOME = "https://www.google.com/"
private const val PINTEREST_HOME = "https://www.pinterest.com/"
private const val PREFS_NAME = "safha_browser_local"
private const val KEY_HISTORY = "history_v1"
private const val KEY_BOOKMARKS = "bookmarks_v1"

private data class BrowserEntry(val title: String, val url: String, val time: Long = System.currentTimeMillis())

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var prefs: android.content.SharedPreferences

    private val bg = Color.rgb(17, 19, 26)
    private val panel = Color.rgb(34, 38, 50)
    private val textColor = Color.rgb(244, 245, 249)
    private val accent = Color.rgb(112, 130, 245)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        buildInterface()
        configureWebView()

        if (savedInstanceState != null) {
            val restored = webView.restoreState(savedInstanceState)
            if (restored == null) webView.loadUrl(GOOGLE_HOME)
        } else {
            webView.loadUrl(GOOGLE_HOME)
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(8), dp(6), dp(8), dp(4))
        }

        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addressBar = EditText(this).apply {
            hint = "ابحث في Google أو اكتب عنوان موقع"
            textSize = 15f
            singleLine = true
            setTextColor(textColor)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(12), 0, dp(8), 0)
            setBackgroundColor(panel)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    navigateFromAddress()
                    true
                } else false
            }
        }
        addressRow.addView(addressBar, LinearLayout.LayoutParams(0, dp(48), 1f))
        addressRow.addView(actionButton("اذهب") { navigateFromAddress() },
            LinearLayout.LayoutParams(dp(68), dp(48)).apply { marginStart = dp(6) })
        root.addView(addressRow)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(
            "رجوع" to { if (webView.canGoBack()) webView.goBack() else toast("ماكو صفحة سابقة") },
            "تقدم" to { if (webView.canGoForward()) webView.goForward() else toast("ماكو صفحة تالية") },
            "تحديث" to { webView.reload() },
            "الرئيسية" to { webView.loadUrl(GOOGLE_HOME) },
            "★" to { addBookmark() },
            "⋮" to { showMenu() }
        ).forEach { (label, action) ->
            controls.addView(actionButton(label, action),
                LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        root.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        val shortcuts = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        shortcuts.addView(actionButton("Google") { webView.loadUrl(GOOGLE_HOME) },
            LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) })
        shortcuts.addView(actionButton("Pinterest") { webView.loadUrl(PINTEREST_HOME) },
            LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        shortcuts.addView(actionButton("السجل") { showEntries(KEY_HISTORY, "سجل التصفح") },
            LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        shortcuts.addView(actionButton("المفضلة") { showEntries(KEY_BOOKMARKS, "المفضلة") },
            LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(4) })
        root.addView(shortcuts, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            progress = 0
            visibility = View.GONE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(webView)
        setContentView(root)
    }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(textColor)
        setTypeface(typeface, Typeface.BOLD)
        setBackgroundColor(panel)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(3), 0, dp(3), 0)
        setOnClickListener { action() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
        settings.userAgentString = settings.userAgentString // Keep the normal WebView UA; do not spoof a browser.

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return routeUrl(request.url.toString())
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = routeUrl(url)

            private fun routeUrl(raw: String): Boolean {
                val uri = Uri.parse(raw)
                return when (uri.scheme?.lowercase()) {
                    "http", "https", "about" -> false // Keep normal web browsing inside this WebView.
                    "mailto", "tel" -> {
                        try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { toast("ماكو تطبيق يدعم هذا الرابط") }
                        true
                    }
                    else -> true // Block javascript:, file:, intent: and unknown schemes.
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url.startsWith("http://") || url.startsWith("https://")) addressBar.setText(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    addressBar.setText(url)
                    addHistory(view.title.orEmpty(), url)
                }
                CookieManager.getInstance().flush()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel() // Never bypass certificate errors.
                toast("تعذر فتح الموقع بأمان")
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) toast("تعذر تحميل الصفحة. تحقق من الإنترنت")
            }

            override fun onSafeBrowsingHit(
                view: WebView,
                request: WebResourceRequest,
                threatType: Int,
                callback: SafeBrowsingResponse
            ) {
                callback.backToSafety(true)
                toast("تم حظر صفحة قد تكون ضارة")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
    }

    private fun navigateFromAddress() {
        val input = addressBar.text.toString().trim()
        if (input.isEmpty()) return
        val url = toUrlOrGoogleSearch(input)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(addressBar.windowToken, 0)
        addressBar.clearFocus()
        webView.loadUrl(url)
    }

    private fun toUrlOrGoogleSearch(input: String): String {
        val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(input)
        if (hasScheme) {
            val uri = Uri.parse(input)
            if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) return input
        } else if (!input.contains(' ') && (input.contains('.') || input.startsWith("localhost"))) {
            return "https://$input"
        }
        return Uri.Builder()
            .scheme("https")
            .authority("www.google.com")
            .appendPath("search")
            .appendQueryParameter("q", input)
            .build()
            .toString()
    }

    private fun addHistory(title: String, url: String) {
        val list = readEntries(KEY_HISTORY).toMutableList()
        list.removeAll { it.url == url }
        list.add(0, BrowserEntry(title.ifBlank { url }, url))
        writeEntries(KEY_HISTORY, list.take(100))
    }

    private fun addBookmark() {
        val url = webView.url
        if (url.isNullOrBlank() || !(url.startsWith("https://") || url.startsWith("http://"))) {
            toast("افتح موقعًا أولًا")
            return
        }
        val list = readEntries(KEY_BOOKMARKS).toMutableList()
        if (list.any { it.url == url }) {
            toast("الموقع موجود بالمفضلة")
            return
        }
        list.add(0, BrowserEntry(webView.title?.ifBlank { url } ?: url, url))
        writeEntries(KEY_BOOKMARKS, list)
        toast("انضاف للمفضلة")
    }

    private fun showEntries(key: String, title: String) {
        val entries = readEntries(key)
        if (entries.isEmpty()) {
            AlertDialog.Builder(this).setTitle(title).setMessage("القائمة فارغة").setPositiveButton("حسنًا", null).show()
            return
        }
        val labels = entries.map { entry ->
            val site = Uri.parse(entry.url).host ?: entry.url
            "${entry.title}\n$site"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, which -> webView.loadUrl(entries[which].url) }
            .setNeutralButton("مسح القائمة") { _, _ -> confirmClear(key, title) }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showMenu() {
        val options = arrayOf("مسح بيانات المواقع وملفات الارتباط", "مسح سجل التصفح", "حول المتصفح")
        AlertDialog.Builder(this)
            .setTitle("إعدادات المتصفح")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmClearSiteData()
                    1 -> confirmClear(KEY_HISTORY, "سجل التصفح")
                    2 -> AlertDialog.Builder(this)
                        .setTitle("صفحة — متصفح داخلي")
                        .setMessage("متصفح Android يعتمد على WebView. سجل التصفح والمفضلة وبيانات المواقع تُخزّن محليًا على هذا الجهاز، ولا يستخدم التطبيق خادمًا خاصًا به.")
                        .setPositiveButton("حسنًا", null).show()
                }
            }
            .show()
    }

    private fun confirmClear(key: String, label: String) {
        AlertDialog.Builder(this)
            .setTitle("مسح $label؟")
            .setMessage("سيتم حذف $label المحفوظ محليًا على هذا الجهاز.")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("مسح") { _, _ ->
                prefs.edit().remove(key).apply()
                toast("تم مسح $label")
            }
            .show()
    }

    private fun confirmClearSiteData() {
        AlertDialog.Builder(this)
            .setTitle("مسح بيانات المواقع؟")
            .setMessage("سيتم حذف ملفات الارتباط والتخزين المحلي للمواقع على هذا الجهاز، وقد تحتاج إلى تسجيل الدخول مجددًا.")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("مسح") { _, _ ->
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                }
                WebStorage.getInstance().deleteAllData()
                webView.clearCache(true)
                webView.clearFormData()
                toast("تم مسح بيانات المواقع")
            }
            .show()
    }

    private fun readEntries(key: String): List<BrowserEntry> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = item.optString("url")
                    if (url.startsWith("https://") || url.startsWith("http://")) {
                        add(BrowserEntry(item.optString("title", url), url, item.optLong("time")))
                    }
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun writeEntries(key: String, entries: List<BrowserEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("title", entry.title)
                put("url", entry.url)
                put("time", entry.time)
            })
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (this::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
