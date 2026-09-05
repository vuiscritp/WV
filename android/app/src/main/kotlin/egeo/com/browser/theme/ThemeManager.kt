package egeo.com.browser.theme

import android.content.Context
import android.content.res.Configuration
import egeo.com.browser.R

/**
 * Quản lý lựa chọn theme của người dùng và phân giải ra style resource cụ thể.
 *
 * - Nếu người dùng chọn SYSTEM: đọc Configuration.uiMode để biết Android đang
 *   ở dark mode hay không, rồi dùng "biến thể sáng"/"biến thể tối" mà người
 *   dùng đã chọn trước đó (mặc định sáng = DAY, tối = NIGHT).
 * - Nếu người dùng chọn cố định 1 trong 4 theme: dùng luôn theme đó.
 */
object ThemeManager {

    private const val PREFS_NAME = "egeo_prefs"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_LIGHT_VARIANT = "theme_light_variant"
    private const val KEY_DARK_VARIANT = "theme_dark_variant"

    fun getMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ThemeMode.fromPref(prefs.getString(KEY_MODE, ThemeMode.SYSTEM.name))
    }

    fun setMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    fun getLightVariant(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_LIGHT_VARIANT, ThemeMode.DAY.name)
        return ThemeMode.fromPref(value).takeIf { it == ThemeMode.MORNING || it == ThemeMode.DAY }
            ?: ThemeMode.DAY
    }

    fun setLightVariant(context: Context, mode: ThemeMode) {
        require(mode == ThemeMode.MORNING || mode == ThemeMode.DAY)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIGHT_VARIANT, mode.name)
            .apply()
    }

    fun getDarkVariant(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_DARK_VARIANT, ThemeMode.NIGHT.name)
        return ThemeMode.fromPref(value).takeIf { it == ThemeMode.NIGHT || it == ThemeMode.MIDNIGHT }
            ?: ThemeMode.NIGHT
    }

    fun setDarkVariant(context: Context, mode: ThemeMode) {
        require(mode == ThemeMode.NIGHT || mode == ThemeMode.MIDNIGHT)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DARK_VARIANT, mode.name)
            .apply()
    }

    /** Theme thực sự sẽ được áp dụng, sau khi đã phân giải SYSTEM (nếu có). */
    fun resolveEffectiveTheme(context: Context): ThemeMode {
        val mode = getMode(context)
        if (mode != ThemeMode.SYSTEM) return mode

        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMask == Configuration.UI_MODE_NIGHT_YES) {
            getDarkVariant(context)
        } else {
            getLightVariant(context)
        }
    }

    fun resolveStyleRes(context: Context): Int {
        return when (resolveEffectiveTheme(context)) {
            ThemeMode.MORNING -> R.style.Theme_Egeo_Morning
            ThemeMode.DAY -> R.style.Theme_Egeo_Day
            ThemeMode.NIGHT -> R.style.Theme_Egeo_Night
            ThemeMode.MIDNIGHT -> R.style.Theme_Egeo_Midnight
            ThemeMode.SYSTEM -> R.style.Theme_Egeo_Day // fallback, không nên tới đây
        }
    }
}
