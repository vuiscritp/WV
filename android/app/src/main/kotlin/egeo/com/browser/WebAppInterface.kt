package egeo.com.browser

import android.webkit.JavascriptInterface

/**
 * Cầu nối JS <-> Android cho trang chủ (assets/home.html).
 * Mỗi phương thức public phải có @JavascriptInterface và chỉ nhận/trả kiểu
 * đơn giản (String, primitive) - giới hạn của WebView JS interface.
 */
class WebAppInterface(private val callbacks: Callbacks) {

    interface Callbacks {
        fun onBridgeOpenUrl(url: String)
        fun onBridgeSearch(query: String)
        fun onBridgeOpenSettings()
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
}
