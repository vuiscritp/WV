package egeo.com.browser

import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import egeo.com.browser.databinding.ActivitySettingsBinding
import egeo.com.browser.search.ALL_SEARCH_ENGINES
import egeo.com.browser.search.searchEngineById
import egeo.com.browser.profile.ProfileHolder
import egeo.com.browser.profile.ProfileManager
import egeo.com.browser.theme.ThemeManager
import egeo.com.browser.theme.ThemeMode

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val themeModeValues = listOf(
        ThemeMode.SYSTEM, ThemeMode.MORNING, ThemeMode.DAY, ThemeMode.NIGHT, ThemeMode.MIDNIGHT
    )
    private val lightVariantValues = listOf(ThemeMode.MORNING, ThemeMode.DAY)
    private val darkVariantValues = listOf(ThemeMode.NIGHT, ThemeMode.MIDNIGHT)
    private var profileIds: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.resolveStyleRes(this))
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSearchEngineSpinner()
        setupProfileSection()
        loadCurrentValues()

        binding.btnSave.setOnClickListener { saveAndFinish() }
        binding.btnDiagnostics.setOnClickListener { openDiagnostics() }
        binding.btnClearProfileData.setOnClickListener { confirmClearProfileData() }
    }

    private fun setupSearchEngineSpinner() {
        val names = ALL_SEARCH_ENGINES.map { it.displayName }
        binding.spinnerSearchEngine.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, names
        )
    }

    private fun setupProfileSection() {
        profileIds = ProfileManager.availableProfileIds()
        val names = profileIds.map { ProfileHolder.displayName(it) }
        binding.spinnerProfile.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, names
        )

        binding.textCurrentProfile.text = getString(
            R.string.label_profile
        ) + ": " + ProfileHolder.displayName(ProfileHolder.currentProfileId)

        if (!ProfileManager.secondaryProfilesSupported()) {
            binding.btnSwitchProfile.isEnabled = false
            Toast.makeText(this, R.string.profile_secondary_unsupported, Toast.LENGTH_LONG).show()
        }

        binding.btnSwitchProfile.setOnClickListener {
            val selectedProfileId = profileIds.getOrNull(binding.spinnerProfile.selectedItemPosition)
                ?: return@setOnClickListener
            if (selectedProfileId == ProfileHolder.currentProfileId) {
                Toast.makeText(this, R.string.already_on_this_profile, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ProfileManager.switchTo(this, selectedProfileId)
            finish()
        }
    }

    private fun loadCurrentValues() {
        binding.spinnerThemeMode.setSelection(themeModeValues.indexOf(ThemeManager.getMode(this)).coerceAtLeast(0))
        binding.spinnerLightVariant.setSelection(lightVariantValues.indexOf(ThemeManager.getLightVariant(this)).coerceAtLeast(0))
        binding.spinnerDarkVariant.setSelection(darkVariantValues.indexOf(ThemeManager.getDarkVariant(this)).coerceAtLeast(0))

        val currentEngine = searchEngineById(AppPrefs.getSearchEngineId(this))
        binding.spinnerSearchEngine.setSelection(ALL_SEARCH_ENGINES.indexOf(currentEngine).coerceAtLeast(0))

        binding.editCustomUa.setText(AppPrefs.getCustomUserAgent(this) ?: "")

        val currentProfileIndex = profileIds.indexOf(ProfileHolder.currentProfileId)
        binding.spinnerProfile.setSelection(currentProfileIndex.coerceAtLeast(0))
    }

    private fun saveAndFinish() {
        val selectedMode = themeModeValues[binding.spinnerThemeMode.selectedItemPosition]
        val selectedLight = lightVariantValues[binding.spinnerLightVariant.selectedItemPosition]
        val selectedDark = darkVariantValues[binding.spinnerDarkVariant.selectedItemPosition]
        val selectedEngine = ALL_SEARCH_ENGINES[binding.spinnerSearchEngine.selectedItemPosition]
        val customUa = binding.editCustomUa.text?.toString()?.trim()

        ThemeManager.setMode(this, selectedMode)
        ThemeManager.setLightVariant(this, selectedLight)
        ThemeManager.setDarkVariant(this, selectedDark)
        AppPrefs.setSearchEngineId(this, selectedEngine.id)
        AppPrefs.setCustomUserAgent(this, customUa)

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
        startActivity(diagnosticsIntent)
    }
}
