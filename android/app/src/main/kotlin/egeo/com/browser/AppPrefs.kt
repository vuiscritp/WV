package egeo.com.browser

import android.content.Context

/**
 * Preferences chung: công cụ tìm kiếm, User-Agent tùy chỉnh, Local API.
 * (App dùng 1 giao diện tối cố định, không còn hệ thống chọn theme.)
 */
object AppPrefs {

    private const val PREFS_NAME = "egeo_prefs"
    private const val KEY_SEARCH_ENGINE = "search_engine"
    private const val KEY_CUSTOM_UA = "custom_ua"
    private const val KEY_API_ENABLED = "api_server_enabled"
    private const val KEY_API_PORT = "api_server_port"

    fun getSearchEngineId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SEARCH_ENGINE, "google") ?: "google"
    }

    fun setSearchEngineId(context: Context, id: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SEARCH_ENGINE, id)
            .apply()
    }

    fun getCustomUserAgent(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_UA, null)?.takeIf { it.isNotBlank() }
    }

    fun setCustomUserAgent(context: Context, ua: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_UA, ua)
            .apply()
    }

    fun isApiServerEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_API_ENABLED, false)
    }

    fun setApiServerEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_API_ENABLED, enabled)
            .apply()
    }

    fun getApiServerPort(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_API_PORT, 8888)
    }

    fun setApiServerPort(context: Context, port: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_API_PORT, port)
            .apply()
    }
}
