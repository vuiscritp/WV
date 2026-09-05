package egeo.com.browser

import android.content.Context

/**
 * Preferences chung ngoài phần theme (theme có ThemeManager riêng).
 */
object AppPrefs {

    private const val PREFS_NAME = "egeo_prefs"
    private const val KEY_SEARCH_ENGINE = "search_engine"
    private const val KEY_CUSTOM_UA = "custom_ua"

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
}
