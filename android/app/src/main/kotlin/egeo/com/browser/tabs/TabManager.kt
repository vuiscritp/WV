package egeo.com.browser.tabs

import android.content.Context
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * Quản lý nhiều tab, mỗi tab là 1 WebView THẬT riêng biệt (không phải load lại
 * URL trên 1 WebView dùng chung), nên mỗi tab giữ đúng lịch sử back/forward,
 * vị trí cuộn, trạng thái form... của riêng nó. Chỉ WebView của tab đang chọn
 * hiển thị (View.VISIBLE), các tab còn lại View.GONE nhưng vẫn sống trong bộ nhớ.
 *
 * configureWebView: nơi caller (MainActivity) thiết lập JS/cookie/UA/WebViewClient...
 * cho mỗi WebView mới tạo, để TabManager không cần biết chi tiết cấu hình đó.
 */
class TabManager(
    private val container: FrameLayout,
    private val configureWebView: (WebView) -> Unit,
    private val onTabsChanged: () -> Unit
) {
    private val tabs = mutableListOf<BrowserTab>()
    private var currentIndex = -1
    private var nextId = 1L

    val tabCount: Int get() = tabs.size
    val currentTab: BrowserTab? get() = tabs.getOrNull(currentIndex)
    val currentIndexValue: Int get() = currentIndex

    fun allTabs(): List<BrowserTab> = tabs.toList()

    fun findIndexByWebView(webView: WebView): Int = tabs.indexOfFirst { it.webView === webView }

    fun addTab(context: Context): BrowserTab {
        val webView = WebView(context)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        configureWebView(webView)
        webView.visibility = View.GONE
        container.addView(webView)

        val tab = BrowserTab(id = nextId++, webView = webView)
        tabs.add(tab)
        switchTo(tabs.size - 1)
        return tab
    }

    fun switchTo(index: Int) {
        if (index !in tabs.indices) return
        tabs.getOrNull(currentIndex)?.webView?.visibility = View.GONE
        currentIndex = index
        tabs[currentIndex].webView.visibility = View.VISIBLE
        onTabsChanged()
    }

    fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val wasCurrent = index == currentIndex
        val tab = tabs.removeAt(index)
        container.removeView(tab.webView)
        tab.webView.stopLoading()
        tab.webView.destroy()

        when {
            tabs.isEmpty() -> {
                currentIndex = -1
                onTabsChanged()
            }
            wasCurrent -> {
                val newIndex = index.coerceAtMost(tabs.size - 1)
                switchTo(newIndex)
            }
            else -> {
                if (index < currentIndex) currentIndex -= 1
                onTabsChanged()
            }
        }
    }

    fun closeCurrentTab() {
        if (currentIndex in tabs.indices) closeTab(currentIndex)
    }

    fun destroyAll() {
        tabs.forEach {
            container.removeView(it.webView)
            it.webView.destroy()
        }
        tabs.clear()
        currentIndex = -1
    }
}
