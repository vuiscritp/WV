package egeo.com.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import egeo.com.browser.databinding.ActivityMainBinding
import egeo.com.browser.search.searchEngineById
import egeo.com.browser.tabs.BrowserTab
import egeo.com.browser.tabs.TabManager
import egeo.com.browser.util.UserAgentUtil

open class MainActivity : AppCompatActivity() {

    companion object {
        private const val HOME_URL = "file:///android_asset/home.html"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var tabManager: TabManager

    private val recordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Dùng ở lần bấm mic/mở web tiếp theo (đã cấp quyền hay chưa). */ }

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) {
                // voice -> search via current tab
                val tab = tabManager.currentTab
                if (tab != null) {
                    tab.isHomePage = false
                    val engine = searchEngineById(AppPrefs.getSearchEngineId(this@MainActivity))
                    tab.webView.loadUrl(engine.buildSearchUrl(text))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Egeo)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tabManager = TabManager(
            container = binding.webViewContainer,
            configureWebView = ::configureNewWebView,
            onTabsChanged = ::onTabsChanged
        )

        setupAddressBar()
        setupNavigationButtons()
        setupBackNavigation()
        requestStartupPermissionsIfNeeded()
        egeo.com.browser.api.BrowserApiBridge.attach(this)

        if (tabManager.tabCount == 0) {
            openNewTab()
        }
    }

    override fun onDestroy() {
        egeo.com.browser.api.BrowserApiBridge.detach(this)
        tabManager.destroyAll()
        super.onDestroy()
    }

    private fun requestStartupPermissionsIfNeeded() {
        // Chỉ xin quyền cho tính năng THẬT SỰ đang có (tìm kiếm bằng giọng nói).
        // Các quyền khác (thông báo, overlay, chạy nền...) sẽ chỉ xin khi tính
        // năng tương ứng thực sự được xây ở phase sau - tránh xin quyền cho
        // tính năng chưa tồn tại.
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // ---------------------------------------------------------------------
    // Tab
    // ---------------------------------------------------------------------

    private fun openNewTab() {
        val tab = tabManager.addTab(this)
        tab.isHomePage = true
        tab.title = "Trang mới"
        tab.webView.loadUrl(HOME_URL)
        bindCurrentTabToUi()
    }

    private fun onTabsChanged() {
        renderTabStrip()
        bindCurrentTabToUi()
        egeo.com.browser.api.ApiEventBus.emit(
            "tabs_changed",
            org.json.JSONObject().put("tab_count", tabManager.tabCount)
        )
    }

    private fun bindCurrentTabToUi() {
        // UI do HTML chrome đảm nhiệm; native chỉ quản WebView/tab
        updateNavButtonsState()
    }

    private fun renderTabStrip() {
        // Thanh tab native đã bỏ — UI tab nằm trong home.html
    }

    private fun showTabSwitcher() {
        val tabs = tabManager.allTabs()
        if (tabs.isEmpty()) return
        val titles = tabs.map { if (it.isHomePage) "Trang mới" else it.title.ifBlank { it.url } }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.tabs_dialog_title)
            .setSingleChoiceItems(titles, tabManager.currentIndexValue) { dialog, which ->
                tabManager.switchTo(which)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------------
    // WebView setup (gọi 1 lần cho mỗi WebView mới, mỗi tab 1 instance riêng)
    // ---------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureNewWebView(webView: WebView) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.allowFileAccess = true

        val customUa = AppPrefs.getCustomUserAgent(this)
        settings.userAgentString = customUa ?: UserAgentUtil.withoutWvMarker(settings.userAgentString)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webView.addJavascriptInterface(
            WebAppInterface(object : WebAppInterface.Callbacks {
                override fun onBridgeOpenUrl(url: String) {
                    runOnUiThread {
                        val tab = tabManager.allTabs().find { it.webView === webView } ?: return@runOnUiThread
                        tab.isHomePage = false
                        webView.loadUrl(normalizeUrl(url))
                    }
                }

                override fun onBridgeSearch(query: String) {
                    runOnUiThread {
                        val tab = tabManager.allTabs().find { it.webView === webView } ?: return@runOnUiThread
                        tab.isHomePage = false
                        val engine = searchEngineById(AppPrefs.getSearchEngineId(this@MainActivity))
                        webView.loadUrl(engine.buildSearchUrl(query))
                    }
                }

                override fun onBridgeOpenSettings() {
                    runOnUiThread { openSettingsScreen() }
                }

                override fun onBridgeNewTab() {
                    runOnUiThread { openNewTab() }
                }

                override fun onBridgeCloseTab() {
                    runOnUiThread {
                        tabManager.closeCurrentTab()
                        if (tabManager.tabCount == 0) openNewTab()
                    }
                }

                override fun onBridgeHistory() {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, R.string.toast_history_empty, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onBridgeDownloads() {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, R.string.toast_downloads_empty, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onBridgeBookmarks() {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, R.string.toast_bookmarks_empty, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onBridgeReload() {
                    runOnUiThread {
                        tabManager.currentTab?.webView?.reload()
                    }
                }

                override fun onBridgeNavigate(query: String) {
                    runOnUiThread { submitAddressBar(query) }
                }
            }),
            "AndroidBridge"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val tab = tabManager.allTabs().find { it.webView === view } ?: return
                if (isCurrentTab(tab)) {
                    // progress UI removed (HTML chrome)
                }
                egeo.com.browser.api.ApiEventBus.emit(
                    "navigation_started",
                    org.json.JSONObject().put("tab_id", tab.id).put("url", url.orEmpty())
                )
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                val tab = tabManager.allTabs().find { it.webView === view } ?: return
                tab.url = url.orEmpty()
                tab.title = view.title?.takeIf { it.isNotBlank() } ?: tab.url

                if (isCurrentTab(tab)) {
                    // address/progress UI removed (HTML chrome)
                    updateNavButtonsState()
                }
                renderTabStrip()
                egeo.com.browser.api.ApiEventBus.emit(
                    "navigation_finished",
                    org.json.JSONObject().put("tab_id", tab.id).put("url", tab.url).put("title", tab.title)
                )
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError?) {
                // An toàn: không tự ý bỏ qua lỗi chứng chỉ SSL.
                handler.cancel()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                val tab = tabManager.allTabs().find { it.webView === view } ?: return
                if (isCurrentTab(tab)) {
                    // progress removed
                }
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                super.onReceivedTitle(view, title)
                val tab = tabManager.allTabs().find { it.webView === view } ?: return
                if (!title.isNullOrBlank()) tab.title = title
                renderTabStrip()
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val wantsAudio = request.resources.any { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                if (!wantsAudio) {
                    request.deny()
                    return
                }
                val granted = ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    request.grant(request.resources)
                } else {
                    request.deny()
                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    private fun isCurrentTab(tab: BrowserTab): Boolean = tabManager.currentTab?.id == tab.id

    // ---------------------------------------------------------------------
    // Thanh địa chỉ / điều hướng
    // ---------------------------------------------------------------------

    private fun setupAddressBar() {
        // UI chrome nằm hoàn toàn trong HTML (home.html)
    }

    private fun setupNavigationButtons() {
        // UI chrome nằm hoàn toàn trong HTML (home.html)
    }

    private fun showOverflowMenu(anchor: View) {
        // Menu ba chấm nằm trong HTML
    }

    private fun startNativeVoiceSearch() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.voice_not_supported, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateNavButtonsState() {
        // Native nav buttons removed
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this) {
            val webView = tabManager.currentTab?.webView
            when {
                webView != null && webView.canGoBack() -> webView.goBack()
                tabManager.tabCount > 1 -> tabManager.closeCurrentTab()
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }
    }

    private fun submitAddressBar(input: String = "") {
        val q = input.trim()
        if (q.isEmpty()) return
        val tab = tabManager.currentTab ?: return
        tab.isHomePage = false
        if (isLikelyUrl(q)) {
            tab.webView.loadUrl(normalizeUrl(q))
        } else {
            tab.webView.loadUrl(buildSearchUrl(q))
        }
    }


    fun apiCreateTab(): org.json.JSONObject {
        openNewTab()
        val tab = tabManager.currentTab
        return org.json.JSONObject()
            .put("id", tab?.id ?: -1)
            .put("title", "Trang mới")
            .put("is_home", true)
    }

    fun apiCloseTab(tabId: Long): Boolean {
        val index = tabManager.allTabs().indexOfFirst { it.id == tabId }
        if (index == -1) return false
        tabManager.closeTab(index)
        if (tabManager.tabCount == 0) openNewTab()
        return true
    }

    fun apiSwitchTab(tabId: Long): Boolean {
        val index = tabManager.allTabs().indexOfFirst { it.id == tabId }
        if (index == -1) return false
        tabManager.switchTo(index)
        return true
    }

    fun apiNavigateTab(tabId: Long, url: String): Boolean {
        val tab = tabManager.allTabs().find { it.id == tabId } ?: return false
        tab.isHomePage = false
        tab.webView.loadUrl(normalizeUrl(url))
        return true
    }
}
