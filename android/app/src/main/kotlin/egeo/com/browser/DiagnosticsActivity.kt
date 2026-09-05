package egeo.com.browser

import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import egeo.com.browser.databinding.ActivityDiagnosticsBinding
import egeo.com.browser.profile.ProfileHolder
import egeo.com.browser.theme.ThemeManager
import egeo.com.browser.util.ProcessUtils

class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagnosticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.resolveStyleRes(this))
        super.onCreate(savedInstanceState)

        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.diagText.text = buildDiagnosticsText()
    }

    private fun buildDiagnosticsText(): String {
        val processName = ProcessUtils.currentProcessName(this)
        val profileId = ProfileHolder.currentProfileId
        val webViewVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" }
                    ?: "Không xác định"
            } catch (e: Exception) {
                "Không xác định"
            }
        } else {
            "Cần Android 8.0+ để đọc trực tiếp"
        }

        val tabCount = intent.getIntExtra(EXTRA_TAB_COUNT, -1)
        val currentUrl = intent.getStringExtra(EXTRA_CURRENT_URL)

        return buildString {
            appendLine("=== Egeo Diagnostics (v1) ===")
            appendLine()
            appendLine("Android version : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thiết bị        : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("WebView         : $webViewVersion")
            appendLine()
            appendLine("Tiến trình      : $processName")
            appendLine("Hồ sơ hiện tại  : ${ProfileHolder.displayName(profileId)} ($profileId)")
            appendLine()
            if (tabCount >= 0) {
                appendLine("Số tab đang mở  : $tabCount")
            }
            if (!currentUrl.isNullOrBlank()) {
                appendLine("URL tab hiện tại: $currentUrl")
            }
            appendLine()
            appendLine("App version     : ${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            appendLine("Build type      : ${BuildConfig.BUILD_TYPE}")
        }
    }

    companion object {
        const val EXTRA_TAB_COUNT = "extra_tab_count"
        const val EXTRA_CURRENT_URL = "extra_current_url"
    }
}
