package egeo.com.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import egeo.com.browser.databinding.ActivityMainBinding
import egeo.com.browser.search.SearchEngine
import egeo.com.browser.search.searchEngineById
import egeo.com.browser.theme.ThemeManager
import egeo.com.browser.theme.ThemeMode
import egeo.com.browser.util.UserAgentUtil

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var appliedThemeAtCreate: ThemeMode = ThemeMode.DAY

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        appliedThemeAtCreate = ThemeManager.resolveEffectiveTheme(this)
        setTheme(ThemeManager.resolveStyleRes(this))
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupAddressBar()
        setupNavigationButtons()
        setupBackNavigation()

        if (savedInstanceState == null) {
            loadHomePage()
        }
    }

    override fun onResume() {
        super.onResume()
        // Nếu theme hệ thống đổi trong lúc app ở background (vd. Android chuyển
        // Dark mode) và người dùng đang để chế độ SYSTEM, áp dụng lại theme.
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        val customUa = AppPrefs.getCustomUserAgent(this)
        settings.userAgentString = customUa ?: UserAgentUtil.withoutWvMarker(settings.userAgentString)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = android.view.View.GONE
                if (url != null && !binding.editAddress.isFocused) {
                    binding.editAddress.setText(url)
                }
                updateNavButtonsState()
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = android.view.View.VISIBLE
                binding.progressBar.progress = 0
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                binding.progressBar.progress = newProgress
            }
        }
    }

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
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupNavigationButtons() {
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) binding.webView.goBack()
        }
        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) binding.webView.goForward()
        }
        binding.btnReload.setOnClickListener {
            binding.webView.reload()
        }
    }

    private fun updateNavButtonsState() {
        binding.btnBack.isEnabled = binding.webView.canGoBack()
        binding.btnForward.isEnabled = binding.webView.canGoForward()
    }

    private fun submitAddressBar() {
        val input = binding.editAddress.text?.toString()?.trim().orEmpty()
        if (input.isEmpty()) return

        val url = if (isLikelyUrl(input)) normalizeUrl(input) else buildSearchUrl(input)
        binding.webView.loadUrl(url)
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

    private fun loadHomePage() {
        // Trang chủ phase 1: mở trang chủ của công cụ tìm kiếm đang chọn.
        val engine = searchEngineById(AppPrefs.getSearchEngineId(this))
        val homeUrl = when (engine) {
            SearchEngine.Google -> "https://www.google.com"
            SearchEngine.Bing -> "https://www.bing.com"
            SearchEngine.CocCoc -> "https://coccoc.com"
            SearchEngine.DuckDuckGo -> "https://duckduckgo.com"
        }
        binding.webView.loadUrl(homeUrl)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this) {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }
}
