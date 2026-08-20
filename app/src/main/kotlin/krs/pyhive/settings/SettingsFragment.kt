package krs.pyhive.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import krs.pyhive.PyHiveApp
import krs.pyhive.R
import krs.pyhive.preferences.AppPreferences

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)

        configureTokenPreference()

        configureNumericPreference(
            key = AppPreferences.KEY_API_PORT,
            minValue = AppPreferences.MIN_PORT,
            maxValue = AppPreferences.MAX_PORT,
            invalidMessageRes = R.string.pref_invalid_port
        )
        configureNumericPreference(
            key = AppPreferences.KEY_DEFAULT_TASK_TIMEOUT_SECONDS,
            minValue = AppPreferences.MIN_TIMEOUT_SECONDS,
            maxValue = AppPreferences.MAX_TIMEOUT_SECONDS,
            invalidMessageRes = R.string.pref_invalid_timeout
        )
        configureNumericPreference(
            key = AppPreferences.KEY_CLEANUP_AGE_DAYS,
            minValue = AppPreferences.MIN_CLEANUP_DAYS,
            maxValue = AppPreferences.MAX_CLEANUP_DAYS,
            invalidMessageRes = R.string.pref_invalid_cleanup_days
        )
        configureNumericPreference(
            key = AppPreferences.KEY_MAX_TASK_MEMORY_MB,
            minValue = AppPreferences.MIN_MAX_TASK_MEMORY_MB,
            maxValue = AppPreferences.MAX_MAX_TASK_MEMORY_MB,
            invalidMessageRes = R.string.pref_invalid_max_task_memory
        )
        configureNumericPreference(
            key = AppPreferences.KEY_MAX_PAYLOAD_SIZE_MB,
            minValue = AppPreferences.MIN_MAX_PAYLOAD_SIZE_MB,
            maxValue = AppPreferences.MAX_MAX_PAYLOAD_SIZE_MB,
            invalidMessageRes = R.string.pref_invalid_max_payload_size
        )
        refreshDynamicSummaries()
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        refreshDynamicSummaries()
    }

    override fun onPause() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null) return

        refreshDynamicSummaries()

        when (key) {
            AppPreferences.KEY_AUTO_START_SERVER -> {
                val app = PyHiveApp.getInstance()
                val appPreferences = PyHiveApp.getAppPreferences()
                if (appPreferences.autoStartServer()) {
                    app.startApiServerIfNeeded()
                } else {
                    app.stopApiServer()
                }
            }
            AppPreferences.KEY_API_PORT -> {
                val app = PyHiveApp.getInstance()
                if (app.isApiServerRunning()) {
                    app.restartApiServer()
                    Toast.makeText(requireContext(), R.string.pref_restart_applied, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.pref_restart_required, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun configureNumericPreference(
        key: String,
        minValue: Int,
        maxValue: Int,
        invalidMessageRes: Int
    ) {
        val editPreference = findPreference<EditTextPreference>(key) ?: return

        editPreference.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }

        editPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val parsed = (newValue as? String)?.toIntOrNull()
            if (parsed == null || parsed !in minValue..maxValue) {
                Toast.makeText(requireContext(), invalidMessageRes, Toast.LENGTH_SHORT).show()
                false
            } else {
                true
            }
        }
    }

    private fun configureTokenPreference() {
        val tokenPreference = findPreference<EditTextPreference>(AppPreferences.KEY_CUSTOM_API_TOKEN) ?: return

        tokenPreference.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            editText.setText("")
        }

        tokenPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val token = (newValue as? String)?.trim().orEmpty()
            if (token.isEmpty()) {
                Toast.makeText(requireContext(), R.string.pref_custom_token_invalid, Toast.LENGTH_SHORT).show()
                return@OnPreferenceChangeListener false
            }

            PyHiveApp.getAuthManager().setToken(token)
            Toast.makeText(requireContext(), R.string.pref_custom_token_saved, Toast.LENGTH_SHORT).show()
            refreshDynamicSummaries()

            // Do not persist raw token in default SharedPreferences.
            false
        }
    }

    private fun refreshDynamicSummaries() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val tokenPreference = findPreference<EditTextPreference>(AppPreferences.KEY_CUSTOM_API_TOKEN)
        val token = PyHiveApp.getAuthManager().getToken()
        val tokenSummary = if (token.isNullOrEmpty()) {
            getString(R.string.pref_custom_token_not_set)
        } else {
            val masked = if (token.length > 20) "${token.take(20)}..." else token
            getString(R.string.pref_custom_token_masked, masked)
        }
        tokenPreference?.summary = tokenSummary
        tokenPreference?.text = ""

        findPreference<EditTextPreference>(AppPreferences.KEY_API_PORT)?.summary =
            sharedPreferences.getString(AppPreferences.KEY_API_PORT, AppPreferences.DEFAULT_PORT.toString())

        findPreference<EditTextPreference>(AppPreferences.KEY_DEFAULT_TASK_TIMEOUT_SECONDS)?.summary =
            sharedPreferences.getString(
                AppPreferences.KEY_DEFAULT_TASK_TIMEOUT_SECONDS,
                AppPreferences.DEFAULT_TIMEOUT_SECONDS.toString()
            )

        findPreference<EditTextPreference>(AppPreferences.KEY_CLEANUP_AGE_DAYS)?.summary =
            sharedPreferences.getString(
                AppPreferences.KEY_CLEANUP_AGE_DAYS,
                AppPreferences.DEFAULT_CLEANUP_DAYS.toString()
            )

        findPreference<EditTextPreference>(AppPreferences.KEY_MAX_TASK_MEMORY_MB)?.summary =
            sharedPreferences.getString(
                AppPreferences.KEY_MAX_TASK_MEMORY_MB,
                AppPreferences.DEFAULT_MAX_TASK_MEMORY_MB.toString()
            )

        findPreference<EditTextPreference>(AppPreferences.KEY_MAX_PAYLOAD_SIZE_MB)?.summary =
            sharedPreferences.getString(
                AppPreferences.KEY_MAX_PAYLOAD_SIZE_MB,
                AppPreferences.DEFAULT_MAX_PAYLOAD_SIZE_MB.toString()
            )
    }
}
