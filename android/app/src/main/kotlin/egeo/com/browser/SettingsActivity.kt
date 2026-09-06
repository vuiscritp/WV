package egeo.com.browser

import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import egeo.com.browser.api.ApiKeyStore
import egeo.com.browser.api.ApiServerService
import egeo.com.browser.databinding.ActivitySettingsBinding
import egeo.com.browser.profile.ProfileHolder
import egeo.com.browser.profile.ProfileManager
import egeo.com.browser.search.ALL_SEARCH_ENGINES
import egeo.com.browser.search.searchEngineById

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var apiKeyStore: ApiKeyStore

    /** Hồ sơ đang được chỉnh sửa - LUÔN lấy từ Intent (do Settings chạy ở
     * tiến trình mặc định bất kể mở từ hồ sơ nào), không tự đọc ProfileHolder. */
    private val profileId: String by lazy {
        intent.getStringExtra(EXTRA_PROFILE_ID) ?: ProfileHolder.DEFAULT_PROFILE_ID
    }

    private var profileIds: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Egeo)
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        apiKeyStore = ApiKeyStore(this, profileId)

        setupSearchEngineDropdown()
        setupProfileSection()
        setupLocalApiSection()
        loadCurrentValues()

        binding.btnSave.setOnClickListener { saveAndFinish() }
        binding.btnDiagnostics.setOnClickListener { openDiagnostics() }
        binding.btnClearProfileData.setOnClickListener { confirmClearProfileData() }
    }

    // -----------------------------------------------------------------
    // Dropdown (AutoCompleteTextView kiểu Exposed Dropdown Menu)
    // -----------------------------------------------------------------

    private fun bindDropdown(view: AutoCompleteTextView, items: List<String>) {
        view.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, items))
    }

    private fun selectedIndex(view: AutoCompleteTextView, items: List<String>): Int =
        items.indexOf(view.text?.toString()).coerceAtLeast(0)

    private fun setupSearchEngineDropdown() {
        bindDropdown(binding.ddSearchEngine, ALL_SEARCH_ENGINES.map { it.displayName })
    }

    private fun setupProfileSection() {
        profileIds = ProfileManager.availableProfileIds()
        bindDropdown(binding.ddProfile, profileIds.map { ProfileHolder.displayName(it) })

        binding.textCurrentProfile.text = getString(R.string.label_profile) + ": " +
            ProfileHolder.displayName(profileId)

        if (!ProfileManager.secondaryProfilesSupported()) {
            binding.btnSwitchProfile.isEnabled = false
            Toast.makeText(this, R.string.profile_secondary_unsupported, Toast.LENGTH_LONG).show()
        }

        binding.btnSwitchProfile.setOnClickListener {
            val names = profileIds.map { ProfileHolder.displayName(it) }
            val selectedProfileId = profileIds.getOrNull(selectedIndex(binding.ddProfile, names))
                ?: return@setOnClickListener
            if (selectedProfileId == profileId) {
                Toast.makeText(this, R.string.already_on_this_profile, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ProfileManager.switchTo(this, selectedProfileId)
            finish()
        }
    }

    // -----------------------------------------------------------------
    // Local API
    // -----------------------------------------------------------------

    private fun setupLocalApiSection() {
        refreshApiStatusText()

        binding.switchApiServer.setOnCheckedChangeListener { _, isChecked ->
            AppPrefs.setApiServerEnabled(this, isChecked)
            applyApiServerState(isChecked)
        }

        binding.btnCreateApiKey.setOnClickListener { showCreateApiKeyDialog() }
        binding.btnManageApiKeys.setOnClickListener { showManageApiKeysDialog() }
    }

    private fun refreshApiStatusText() {
        val port = AppPrefs.getApiServerPort(this)
        val enabled = AppPrefs.isApiServerEnabled(this)
        val keyCount = apiKeyStore.listKeys().count { it.isValid }
        binding.textApiStatus.text = if (enabled) {
            "Đang chạy ở 127.0.0.1:$port · $keyCount API key hợp lệ"
        } else {
            "Đang tắt · $keyCount API key hợp lệ"
        }
    }

    private fun applyApiServerState(enabled: Boolean) {
        val port = binding.editApiPort.text?.toString()?.toIntOrNull() ?: AppPrefs.getApiServerPort(this)
        val serviceClass = ProfileManager.apiServiceClassFor(profileId)
        if (enabled) {
            val intent = Intent(this, serviceClass).putExtra(ApiServerService.EXTRA_PORT, port)
            ContextCompat.startForegroundService(this, intent)
        } else {
            stopService(Intent(this, serviceClass))
        }
        refreshApiStatusText()
    }

    private fun showCreateApiKeyDialog() {
        val input = EditText(this).apply { hint = getString(R.string.dialog_new_api_key_hint) }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_new_api_key_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val label = input.text?.toString().orEmpty()
                val key = apiKeyStore.generate(label, ApiKeyStore.TTL_30_DAYS)
                AlertDialog.Builder(this)
                    .setTitle(R.string.action_create_api_key)
                    .setMessage(getString(R.string.dialog_new_api_key_created, key.token))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                refreshApiStatusText()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showManageApiKeysDialog() {
        val keys = apiKeyStore.listKeys()
        if (keys.isEmpty()) {
            Toast.makeText(this, R.string.no_api_keys_yet, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = keys.map { key ->
            val status = when {
                key.revoked -> "đã thu hồi"
                key.isExpired -> "hết hạn"
                else -> "hợp lệ"
            }
            "${key.label} (${key.maskedToken}) - $status"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_manage_keys_title)
            .setItems(labels) { _, which ->
                val key = keys[which]
                if (key.isValid) {
                    apiKeyStore.revoke(key.id)
                    Toast.makeText(this, R.string.api_key_revoked, Toast.LENGTH_SHORT).show()
                    refreshApiStatusText()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // -----------------------------------------------------------------
    // Load / Save
    // -----------------------------------------------------------------

    private fun loadCurrentValues() {
        val currentEngine = searchEngineById(AppPrefs.getSearchEngineId(this))
        binding.ddSearchEngine.setText(currentEngine.displayName, false)

        binding.editCustomUa.setText(AppPrefs.getCustomUserAgent(this) ?: "")

        val currentProfileIndex = profileIds.indexOf(profileId).coerceAtLeast(0)
        binding.ddProfile.setText(ProfileHolder.displayName(profileIds.getOrElse(currentProfileIndex) { profileId }), false)

        binding.editApiPort.setText(AppPrefs.getApiServerPort(this).toString())
        binding.switchApiServer.isChecked = AppPrefs.isApiServerEnabled(this)
    }

    private fun saveAndFinish() {
        val engineNames = ALL_SEARCH_ENGINES.map { it.displayName }
        val selectedEngine = ALL_SEARCH_ENGINES[selectedIndex(binding.ddSearchEngine, engineNames)]
        val customUa = binding.editCustomUa.text?.toString()?.trim()

        AppPrefs.setSearchEngineId(this, selectedEngine.id)
        AppPrefs.setCustomUserAgent(this, customUa)

        val port = binding.editApiPort.text?.toString()?.toIntOrNull()
        if (port == null || port !in 1024..65535) {
            Toast.makeText(this, R.string.invalid_port, Toast.LENGTH_SHORT).show()
            return
        }
        AppPrefs.setApiServerPort(this, port)
        if (AppPrefs.isApiServerEnabled(this)) {
            // Cổng có thể vừa đổi - khởi động lại service với cổng mới.
            applyApiServerState(true)
        }

        finish()
    }

    private fun confirmClearProfileData() {
        AlertDialog.Builder(this)
            .setTitle(R.string.action_clear_profile_data)
            .setMessage(R.string.confirm_clear_profile_data)
            .setPositiveButton(android.R.string.ok) { _, _ -> clearProfileData() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearProfileData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun openDiagnostics() {
        val tabCount = intent.getIntExtra(DiagnosticsActivity.EXTRA_TAB_COUNT, -1)
        val currentUrl = intent.getStringExtra(DiagnosticsActivity.EXTRA_CURRENT_URL)
        val diagnosticsIntent = Intent(this, DiagnosticsActivity::class.java)
            .putExtra(DiagnosticsActivity.EXTRA_TAB_COUNT, tabCount)
            .putExtra(DiagnosticsActivity.EXTRA_CURRENT_URL, currentUrl)
            .putExtra(DiagnosticsActivity.EXTRA_PROFILE_ID, profileId)
        startActivity(diagnosticsIntent)
    }

    companion object {
        const val EXTRA_PROFILE_ID = "extra_profile_id"
    }
}
