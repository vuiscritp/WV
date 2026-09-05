package egeo.com.browser

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import egeo.com.browser.databinding.ActivitySettingsBinding
import egeo.com.browser.search.SearchEngine
import egeo.com.browser.theme.ThemeManager
import egeo.com.browser.theme.ThemeMode

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val themeModeValues = listOf(
        ThemeMode.SYSTEM, ThemeMode.MORNING, ThemeMode.DAY, ThemeMode.NIGHT, ThemeMode.MIDNIGHT
    )
    private val lightVariantValues = listOf(ThemeMode.MORNING, ThemeMode.DAY)
    private val darkVariantValues = listOf(ThemeMode.NIGHT, ThemeMode.MIDNIGHT)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.resolveStyleRes(this))
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSearchEngineSpinner()
        loadCurrentValues()

        binding.btnSave.setOnClickListener { saveAndFinish() }
    }

    private fun setupSearchEngineSpinner() {
        val names = SearchEngine.ALL.map { it.displayName }
        binding.spinnerSearchEngine.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, names
        )
    }

    private fun loadCurrentValues() {
        binding.spinnerThemeMode.setSelection(themeModeValues.indexOf(ThemeManager.getMode(this)).coerceAtLeast(0))
        binding.spinnerLightVariant.setSelection(lightVariantValues.indexOf(ThemeManager.getLightVariant(this)).coerceAtLeast(0))
        binding.spinnerDarkVariant.setSelection(darkVariantValues.indexOf(ThemeManager.getDarkVariant(this)).coerceAtLeast(0))

        val currentEngine = SearchEngine.byId(AppPrefs.getSearchEngineId(this))
        binding.spinnerSearchEngine.setSelection(SearchEngine.ALL.indexOf(currentEngine).coerceAtLeast(0))

        binding.editCustomUa.setText(AppPrefs.getCustomUserAgent(this) ?: "")
    }

    private fun saveAndFinish() {
        val selectedMode = themeModeValues[binding.spinnerThemeMode.selectedItemPosition]
        val selectedLight = lightVariantValues[binding.spinnerLightVariant.selectedItemPosition]
        val selectedDark = darkVariantValues[binding.spinnerDarkVariant.selectedItemPosition]
        val selectedEngine = SearchEngine.ALL[binding.spinnerSearchEngine.selectedItemPosition]
        val customUa = binding.editCustomUa.text?.toString()?.trim()

        ThemeManager.setMode(this, selectedMode)
        ThemeManager.setLightVariant(this, selectedLight)
        ThemeManager.setDarkVariant(this, selectedDark)
        AppPrefs.setSearchEngineId(this, selectedEngine.id)
        AppPrefs.setCustomUserAgent(this, customUa)

        finish()
    }
}
