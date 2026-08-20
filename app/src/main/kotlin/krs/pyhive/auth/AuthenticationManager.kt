package krs.pyhive.auth

import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom

/**
 * Manages Bearer Token authentication for the REST API
 */
class AuthenticationManager(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val TOKEN_KEY = "api_bearer_token"
        private const val TOKEN_LENGTH = 32
        private const val TOKEN_PREFIX = "Bearer "
    }

    /**
     * Generate a new secure Bearer token
     */
    fun generateNewToken(): String {
        val random = SecureRandom()
        val bytes = ByteArray(TOKEN_LENGTH)
        random.nextBytes(bytes)
        val token = Base64.encodeToString(bytes, Base64.NO_WRAP)
        saveToken(token)
        return token
    }

    /**
     * Get the current stored token
     */
    fun getToken(): String? {
        return sharedPreferences.getString(TOKEN_KEY, null)
    }

    /**
     * Persist a custom Bearer token provided by the user
     */
    fun setToken(token: String) {
        val normalized = token.trim()
        require(normalized.isNotEmpty()) { "Token cannot be blank" }
        saveToken(normalized)
    }

    /**
     * Save token to preferences
     */
    private fun saveToken(token: String) {
        sharedPreferences.edit().putString(TOKEN_KEY, token).apply()
    }

    /**
     * Validate incoming Bearer token
     */
    fun validateToken(authHeader: String?): Boolean {
        if (authHeader == null || !authHeader.startsWith(TOKEN_PREFIX)) {
            return false
        }

        val incomingToken = authHeader.removePrefix(TOKEN_PREFIX).trim()
        val storedToken = getToken() ?: return false

        return incomingToken.equals(storedToken, ignoreCase = false)
    }

    /**
     * Extract token from Authorization header
     */
    fun extractToken(authHeader: String?): String? {
        if (authHeader == null || !authHeader.startsWith(TOKEN_PREFIX)) {
            return null
        }
        return authHeader.removePrefix(TOKEN_PREFIX).trim()
    }

    /**
     * Clear stored token
     */
    fun clearToken() {
        sharedPreferences.edit().remove(TOKEN_KEY).apply()
    }

    /**
     * Check if token exists
     */
    fun hasToken(): Boolean = getToken() != null
}

/**
 * Custom exception for authentication failures
 */
class AuthenticationException(message: String) : Exception(message)
