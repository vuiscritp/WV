package egeo.com.browser.api

import egeo.com.browser.BuildConfig
import egeo.com.browser.MainActivity
import egeo.com.browser.profile.ProfileHolder
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * Cầu nối trong cùng 1 tiến trình tới MainActivity đang mở (nếu có). Dùng
 * WeakReference để không giữ Activity sống lâu hơn cần thiết (tránh leak).
 * Nếu chưa có MainActivity nào của tiến trình này đang mở, các hàm bên dưới
 * trả về null - server sẽ báo lỗi "trình duyệt chưa mở" thay vì crash.
 */
object BrowserApiBridge {

    @Volatile
    private var activityRef: WeakReference<MainActivity>? = null

    fun attach(activity: MainActivity) {
        activityRef = WeakReference(activity)
    }

    fun detach(activity: MainActivity) {
        if (activityRef?.get() === activity) {
            activityRef = null
        }
    }

    private fun <T> withActivity(block: (MainActivity) -> T): T? {
        val activity = activityRef?.get() ?: return null
        return MainThreadBridge.runBlocking { block(activity) }
    }

    fun isBrowserOpen(): Boolean = activityRef?.get() != null

    fun status(serverPort: Int): JSONObject {
        val o = JSONObject()
        o.put("app_version", BuildConfig.VERSION_NAME)
        o.put("profile", ProfileHolder.currentProfileId)
        o.put("server_port", serverPort)
        o.put("browser_open", isBrowserOpen())
        o.put("tab_count", withActivity { it.apiTabCount() } ?: 0)
        return o
    }

    fun listTabs(): JSONArray? = withActivity { it.apiListTabs() }

    fun createTab(): JSONObject? = withActivity { it.apiCreateTab() }

    fun closeTab(tabId: Long): Boolean = withActivity { it.apiCloseTab(tabId) } ?: false

    fun switchTab(tabId: Long): Boolean = withActivity { it.apiSwitchTab(tabId) } ?: false

    fun navigateTab(tabId: Long, url: String): Boolean = withActivity { it.apiNavigateTab(tabId, url) } ?: false
}
