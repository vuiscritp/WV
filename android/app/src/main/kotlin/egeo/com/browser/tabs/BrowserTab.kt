package egeo.com.browser.tabs

import android.webkit.WebView

data class BrowserTab(
    val id: Long,
    val webView: WebView,
    var title: String = "",
    var url: String = "",
    var isHomePage: Boolean = true
)
