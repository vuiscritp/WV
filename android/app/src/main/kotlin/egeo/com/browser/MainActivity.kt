package egeo.com.browser

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import egeo.com.browser.databinding.ActivityMainBinding
import egeo.com.browser.databinding.ItemTabChipBinding
import egeo.com.browser.search.searchEngineById
import egeo.com.browser.tabs.BrowserTab
import egeo.com.browser.tabs.TabManager
import egeo.com.browser.theme.ThemeManager
import egeo.com.browser.theme.ThemeMode
import egeo.com.browser.util.UserAgentUtil

open class MainActivity : AppCompatActivity() {

    companion object {
        private const val HOME_URL = "file:///android_asset/home.html"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var tabManager: TabManager
    private var appliedThemeAtCreate: ThemeMode = ThemeMode.DAY

    private val recordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Kết quả sẽ được dùng ở lần bấm mic tiếp theo (đã cấp quyền hay chưa). */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedThemeAtCreate = ThemeManager.resolveEffectiveTheme(this)
        setTheme(ThemeManager.resolveStyleRes(this))
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

        if (savedInstanceState == null) {
            openNewTab()
        }
    }

    override fun onResume() {
        super.onResume()
        val current = ThemeManager.resolveEffectiveTheme(this)
        if (current != appliedThemeAtCreate) {
            recreate()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (ThemeManager.getMode(this) == ThemeMode.SYSTEM) {
            val current = ThemeManager.resolveEffectiveTheme(this)
            if (current != appliedThemeAtCreate) {
                recreate()
            }
        }
    }

    override fun onDestroy() {
        tabManager.destroyAll()
        super.onDestroy()
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
    }

    private fun bindCurrentTabToUi() {
        val tab = tabManager.currentTab
        binding.editAddress.setText(if (tab == null || tab.isHomePage) "" else tab.url)
        updateNavButtonsState()
    }

    private fun renderTabStrip() {
        binding.tabStrip.removeAllViews()
        val inflater = LayoutInflater.from(this)
        tabManager.allTabs().forEachIndexed { index, tab ->
            val chip = ItemTabChipBinding.inflate(inflater, binding.tabStrip, false)
            chip.tabTitle.text = if (tab.isHomePage) "Trang mới" else tab.title.ifBlank { tab.url }
            chip.root.isSelected = index == tabManager.currentIndexValue
            chip.root.setOnClickListener {
                tabManager.switchTo(index)
            }
            chip.tabClose.setOnClickListener {
                tabManager.closeTab(index)
                if (tabManager.tabCount == 0) {
                    openNewTab()
                }
            }
            binding.tabStrip.addView(chip.root)
        }
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
            }),
            "AndroidBridge"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val tab = tabManager.allTabs().find { it.webView === view } ?: return
                if (isCurrentTab(tab)) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = 0
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                val tab = tabManager.allTabs().find { it.webView === view } ?: return
                tab.url = url.orEmpty()
                tab.title = view.title?.takeIf { it.isNotBlank() } ?: tab.url
                if (isCurrentTab(tab)) {
                    binding.progressBar.visibility = View.GONE
                    if (!binding.editAddress.isFocused) {
                        binding.editAddress.setText(if (tab.isHomePage) "" else tab.url)
                    }
                    updateNavButtonsState()
                }
                renderTabStrip()
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
                    binding.progressBar.progress = newProgress
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
        binding.editAddress.setOnEditorActionListener { _, actionId, event ->
            val isEnterKey = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER
            if (actionId == EditorInfo.IME_ACTION_GO || isEnterKey) {
                submitAddressBar()
                true
            } else {
                false
            }
        }
        binding.btnGo.setOnClickListener { submitAddressBar() }
        binding.btnSettings.setOnClickListener { openSettingsScreen() }
    }

    private fun setupNavigationButtons() {
        binding.btnBack.setOnClickListener {
            tabManager.currentTab?.webView?.let { if (it.canGoBack()) it.goBack() }
        }
        binding.btnForward.setOnClickListener {
            tabManager.currentTab?.webView?.let { if (it.canGoForward()) it.goForward() }
        }
        binding.btnReload.setOnClickListener {
            tabManager.currentTab?.webView?.reload()
        }
        binding.btnNewTab.setOnClickListener { openNewTab() }
    }

    private fun updateNavButtonsState() {
        val webView = tabManager.currentTab?.webView
        binding.btnBack.isEnabled = webView?.canGoBack() == true
        binding.btnForward.isEnabled = webView?.canGoForward() == true
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

    private fun submitAddressBar() {
        val input = binding.editAddress.text?.toString()?.trim().orEmpty()
        if (input.isEmpty()) return
        val tab = tabManager.currentTab ?: return
        tab.isHomePage = false

        val url = if (isLikelyUrl(input)) normalizeUrl(input) else buildSearchUrl(input)
        tab.webView.loadUrl(url)
        binding.editAddress.clearFocus()
    }

    private fun buildSearchUrl(query: String): String {
        val engine = searchEngineById(AppPrefs.getSearchEngineId(this))
        return engine.buildSearchUrl(query)
    }

    private fun isLikelyUrl(input: String): Boolean {
        if (input.startsWith("http://") || input.startsWith("https://")) return true
        if (input.contains(" ")) return false
        return input.contains(".") && !input.contains("..")
    }

    private fun normalizeUrl(input: String): String {
        return if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else {
            "https://$input"
        }
    }

    private fun openSettingsScreen() {
        val intent = Intent(this, SettingsActivity::class.java)
            .putExtra(DiagnosticsActivity.EXTRA_TAB_COUNT, tabManager.tabCount)
            .putExtra(DiagnosticsActivity.EXTRA_CURRENT_URL, tabManager.currentTab?.url.orEmpty())
        startActivity(intent)
    }
}
