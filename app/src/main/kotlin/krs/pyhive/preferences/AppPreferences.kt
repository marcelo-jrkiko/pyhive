package krs.pyhive.preferences

import android.content.Context
import androidx.preference.PreferenceManager

class AppPreferences(context: Context) {

    companion object {
        const val KEY_AUTO_START_SERVER = "pref_auto_start_server"
        const val KEY_API_PORT = "pref_api_port"
        const val KEY_DEFAULT_TASK_TIMEOUT_SECONDS = "pref_default_task_timeout_seconds"
        const val KEY_CLEANUP_AGE_DAYS = "pref_cleanup_age_days"
        const val KEY_SHOW_FULL_TOKEN = "pref_show_full_token"
        const val KEY_CUSTOM_API_TOKEN = "pref_custom_api_token"

        const val DEFAULT_PORT = 8080
        const val DEFAULT_TIMEOUT_SECONDS = 300
        const val DEFAULT_CLEANUP_DAYS = 7

        const val MIN_PORT = 1024
        const val MAX_PORT = 65535
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 3600
        const val MIN_CLEANUP_DAYS = 1
        const val MAX_CLEANUP_DAYS = 30
    }

    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun autoStartServer(): Boolean =
        preferences.getBoolean(KEY_AUTO_START_SERVER, true)

    fun showFullToken(): Boolean =
        preferences.getBoolean(KEY_SHOW_FULL_TOKEN, false)

    fun apiPort(): Int =
        sanitizePort(preferences.getString(KEY_API_PORT, DEFAULT_PORT.toString()))

    fun defaultTaskTimeoutSeconds(): Long =
        sanitizeTimeout(preferences.getString(KEY_DEFAULT_TASK_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS.toString())).toLong()

    fun cleanupAgeMillis(): Long {
        val cleanupDays = sanitizeCleanupDays(
            preferences.getString(KEY_CLEANUP_AGE_DAYS, DEFAULT_CLEANUP_DAYS.toString())
        )
        return cleanupDays.toLong() * 24L * 60L * 60L * 1000L
    }

    private fun sanitizePort(raw: String?): Int {
        val parsed = raw?.toIntOrNull() ?: DEFAULT_PORT
        return parsed.coerceIn(MIN_PORT, MAX_PORT)
    }

    private fun sanitizeTimeout(raw: String?): Int {
        val parsed = raw?.toIntOrNull() ?: DEFAULT_TIMEOUT_SECONDS
        return parsed.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
    }

    private fun sanitizeCleanupDays(raw: String?): Int {
        val parsed = raw?.toIntOrNull() ?: DEFAULT_CLEANUP_DAYS
        return parsed.coerceIn(MIN_CLEANUP_DAYS, MAX_CLEANUP_DAYS)
    }
}
