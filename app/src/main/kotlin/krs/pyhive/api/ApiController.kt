package krs.pyhive.api

import java.io.BufferedReader
import java.io.BufferedWriter

/**
 * Base contract for API route controllers.
 */
interface ApiController {
    fun supports(route: ApiRoutes.Match): Boolean
    suspend fun handle(route: ApiRoutes.Match, context: ApiRequestContext)
}

/**
 * Request-scoped helpers exposed to route controllers.
 */
class ApiRequestContext(
    private val writer: BufferedWriter,
    private val reader: BufferedReader,
    private val sendJsonResponse: (BufferedWriter, Int, Any) -> Unit,
    private val sendErrorResponse: (BufferedWriter, Int, String) -> Unit,
    private val readPayload: (BufferedReader) -> String,
    private val multipartReader: (BufferedReader, String) -> Map<String, String>,
    private val contentType: String
) {
    fun sendJson(statusCode: Int, body: Any) {
        sendJsonResponse(writer, statusCode, body)
    }

    fun sendError(statusCode: Int, message: String) {
        sendErrorResponse(writer, statusCode, message)
    }

    fun readRequestPayload(): String = readPayload(reader)

    /**
     * Reads a multipart/form-data request body and returns the fields keyed by name.
     */
    fun readMultipartForm(): Map<String, String> {
        val boundary = extractBoundary(contentType)
        if (boundary.isEmpty()) {
            throw IllegalArgumentException("Multipart request missing boundary")
        }
        return multipartReader(reader, boundary)
    }

    private fun extractBoundary(contentType: String): String {
        if (contentType.isEmpty()) return ""
        val parts = contentType.split("boundary=")
        if (parts.size < 2) return ""
        var boundary = parts[1].trim()
        if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length >= 2) {
            boundary = boundary.substring(1, boundary.length - 1)
        }
        return boundary
    }

    inline fun <reified T> parseJson(json: String): T {
        return com.google.gson.Gson().fromJson(json, T::class.java)
    }

    fun parseQueryParams(query: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        query.split("&").forEach { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                params[parts[0]] = parts[1]
            }
        }
        return params
    }
}