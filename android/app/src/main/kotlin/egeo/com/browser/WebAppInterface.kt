package egeo.com.browser

import android.webkit.JavascriptInterface

/**
 * Cầu nối JS <-> Android cho trang chủ (assets/home.html).
 * Mỗi phương thức public phải có @JavascriptInterface.
 */
class WebAppInterface(private val callbacks: Callbacks) {

    interface Callbacks {
        fun onBridgeOpenUrl(url: String)
        fun onBridgeSearch(query: String)
        fun onBridgeOpenSettings()
        fun onBridgeNewTab()
        fun onBridgeCloseTab()
        fun onBridgeHistory()
        fun onBridgeDownloads()
        fun onBridgeBookmarks()
        fun onBridgeReload()
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        callbacks.onBridgeOpenUrl(url)
    }

    @JavascriptInterface
    fun search(query: String) {
        callbacks.onBridgeSearch(query)
    }

    @JavascriptInterface
    fun openSettings() {
        callbacks.onBridgeOpenSettings()
    }

    @JavascriptInterface
    fun newTab() {
        callbacks.onBridgeNewTab()
    }

    @JavascriptInterface
    fun closeTab() {
        callbacks.onBridgeCloseTab()
    }

    @JavascriptInterface
    fun openHistory() {
        callbacks.onBridgeHistory()
    }

    @JavascriptInterface
    fun openDownloads() {
        callbacks.onBridgeDownloads()
    }

    @JavascriptInterface
    fun openBookmarks() {
        callbacks.onBridgeBookmarks()
    }

    @JavascriptInterface
    fun reload() {
        callbacks.onBridgeReload()
    }
}
