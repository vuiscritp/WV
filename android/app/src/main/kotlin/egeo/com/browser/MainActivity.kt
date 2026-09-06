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
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import egeo.com.browser.databinding.ActivityMainBinding
import egeo.com.browser.databinding.ItemTabChipBinding
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
                binding.editAddress.setText(text)
                submitAddressBar()
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
                    binding.progressBar.visibility = View.GONE
                    if (!binding.editAddress.isFocused) {
                        binding.editAddress.setText(if (tab.isHomePage) "" else tab.url)
                    }
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
        binding.btnMic.setOnClickListener { startNativeVoiceSearch() }
        binding.btnNewTabTop.setOnClickListener { openNewTab() }
        binding.btnOverflow.setOnClickListener { showOverflowMenu(it) }
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
    }

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.action_settings)
        popup.menu.add(0, 2, 1, R.string.action_close_tab)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    openSettingsScreen()
                    true
                }
                2 -> {
                    tabManager.closeCurrentTab()
                    if (tabManager.tabCount == 0) openNewTab()
                    true
                }
                else -> false
            }
        }
        popup.show()
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
            .putExtra(SettingsActivity.EXTRA_PROFILE_ID, egeo.com.browser.profile.ProfileHolder.currentProfileId)
        startActivity(intent)
    }

    // ---------------------------------------------------------------------
    // Local API (Phần 3) - gọi từ BrowserApiBridge, LUÔN chạy trên main thread
    // (BrowserApiBridge.withActivity đã post qua MainThreadBridge trước khi
    // gọi các hàm này, nên ở đây có thể đụng thẳng vào TabManager/WebView).
    // ---------------------------------------------------------------------

    fun apiTabCount(): Int = tabManager.tabCount

    fun apiListTabs(): org.json.JSONArray {
        val arr = org.json.JSONArray()
        tabManager.allTabs().forEach { tab ->
            arr.put(
                org.json.JSONObject()
                    .put("id", tab.id)
                    .put("title", if (tab.isHomePage) "Trang mới" else tab.title)
                    .put("url", if (tab.isHomePage) "" else tab.url)
                    .put("is_home", tab.isHomePage)
                    .put("is_current", isCurrentTab(tab))
            )
        }
        return arr
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
