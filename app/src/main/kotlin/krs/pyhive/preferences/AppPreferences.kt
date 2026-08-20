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
        const val KEY_MAX_TASK_MEMORY_MB = "pref_max_task_memory_mb"
        const val KEY_MAX_PAYLOAD_SIZE_MB = "http_max_payload_size_mb"

        const val KEY_CORS_ENABLED = "pref_cors_enabled"
        const val KEY_CORS_ALLOWED_ORIGINS = "pref_cors_allowed_origins"
        const val KEY_CORS_ALLOWED_METHODS = "pref_cors_allowed_methods"
        const val KEY_CORS_ALLOWED_HEADERS = "pref_cors_allowed_headers"

        const val DEFAULT_CORS_ENABLED = true
        const val DEFAULT_CORS_ALLOWED_ORIGINS = "*"
        const val DEFAULT_CORS_ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS"
        const val DEFAULT_CORS_ALLOWED_HEADERS = "Authorization, Content-Type"

        const val DEFAULT_PORT = 8080
        const val DEFAULT_TIMEOUT_SECONDS = 300
        const val DEFAULT_CLEANUP_DAYS = 7
        const val DEFAULT_MAX_TASK_MEMORY_MB = 1024
        const val DEFAULT_MAX_PAYLOAD_SIZE_MB = 10

        const val MIN_PORT = 1024
        const val MAX_PORT = 65535
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 3600
        const val MIN_CLEANUP_DAYS = 1
        const val MAX_CLEANUP_DAYS = 30
        const val MIN_MAX_TASK_MEMORY_MB = 0
        const val MAX_MAX_TASK_MEMORY_MB = 8192
        const val MIN_MAX_PAYLOAD_SIZE_MB = 1
        const val MAX_MAX_PAYLOAD_SIZE_MB = 1024
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

    /** Maximum JVM heap (in MB) a task may use; 0 disables the watchdog. */
    fun maxTaskMemoryMb(): Int =
        sanitizeMemoryLimitMb(
            preferences.getString(KEY_MAX_TASK_MEMORY_MB, DEFAULT_MAX_TASK_MEMORY_MB.toString()),
            default = DEFAULT_MAX_TASK_MEMORY_MB
        )

    /** Maximum JVM heap (in bytes) a task may use; 0 disables the watchdog. */
    fun maxTaskMemoryBytes(): Long =
        maxTaskMemoryMb().toLong() * 1024L * 1024L

    /** Maximum HTTP request body (in MB) the API server will accept. */
    fun maxPayloadSizeMb(): Int =
        sanitizeMemoryLimitMb(
            preferences.getString(KEY_MAX_PAYLOAD_SIZE_MB, DEFAULT_MAX_PAYLOAD_SIZE_MB.toString()),
            default = DEFAULT_MAX_PAYLOAD_SIZE_MB
        )

    /** Maximum HTTP request body (in bytes) the API server will accept. */
    fun maxPayloadSizeBytes(): Long =
        maxPayloadSizeMb().toLong() * 1024L * 1024L

    /** Whether CORS support is enabled. */
    fun corsEnabled(): Boolean =
        preferences.getBoolean(KEY_CORS_ENABLED, DEFAULT_CORS_ENABLED)

    /**
     * Comma-separated list of allowed origins (e.g. "https://example.com, http://localhost:3000").
     * Use "*" to allow all origins.
     */
    fun corsAllowedOrigins(): String =
        preferences.getString(KEY_CORS_ALLOWED_ORIGINS, DEFAULT_CORS_ALLOWED_ORIGINS)
            ?.trim().orEmpty().ifEmpty { DEFAULT_CORS_ALLOWED_ORIGINS }

    /**
     * Comma-separated list of allowed HTTP methods (e.g. "GET, POST, OPTIONS").
     */
    fun corsAllowedMethods(): String =
        preferences.getString(KEY_CORS_ALLOWED_METHODS, DEFAULT_CORS_ALLOWED_METHODS)
            ?.trim().orEmpty().ifEmpty { DEFAULT_CORS_ALLOWED_METHODS }

    /**
     * Comma-separated list of allowed request headers (e.g. "Authorization, Content-Type").
     */
    fun corsAllowedHeaders(): String =
        preferences.getString(KEY_CORS_ALLOWED_HEADERS, DEFAULT_CORS_ALLOWED_HEADERS)
            ?.trim().orEmpty().ifEmpty { DEFAULT_CORS_ALLOWED_HEADERS }

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

    private fun sanitizeMemoryLimitMb(raw: String?, default: Int): Int {
        val parsed = raw?.toIntOrNull() ?: default
        return if (parsed <= 0) 0 else parsed
    }
}
