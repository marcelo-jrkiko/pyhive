package krs.pyhive.api

import android.content.Context
import krs.pyhive.auth.AuthenticationManager
import krs.pyhive.models.*
import krs.pyhive.python.PythonRuntimeManager
import krs.pyhive.sandbox.SandboxManager
import krs.pyhive.scheduler.TaskScheduler
import krs.pyhive.preferences.AppPreferences
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * REST API service for Python Task Runner
 * Implements a lightweight HTTP server with Bearer token authentication
 */
class PythonTaskRunnerService(
    private val context: Context,
    private val authManager: AuthenticationManager,
    private val taskScheduler: TaskScheduler,
    private val pythonRuntimeManager: PythonRuntimeManager,
    private val sandboxManager: SandboxManager,
    private val appPreferences: AppPreferences,
    private val port: Int = 8080
) {

    companion object {
        private const val CONTENT_TYPE_JSON = "application/json"
        private const val AUTH_HEADER = "Authorization"
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val controllers: List<ApiController> = listOf(
        TaskApiController(context, taskScheduler)
    )

    fun isRunning(): Boolean = isRunning

    fun getPort(): Int = port

    /**
     * Start the API server
     */
    fun start() {
        if (isRunning) {
            Timber.w("Server is already running")
            return
        }

        scope.launch {
            try {

                serverSocket = ServerSocket(port)
                isRunning = true
                Timber.d("API server started on port $port")

                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: continue
                        scope.launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Timber.e("Error accepting client connection: $e")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e("Error starting API server: $e")
                isRunning = false
            }
        }
    }

    /**
     * Stop the API server
     */
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            scope.cancel()
            Timber.d("API server stopped")
        } catch (e: Exception) {
            Timber.e("Error stopping API server: $e")
        }
    }

    /**
     * Handle incoming client request
     */
    private suspend fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, StandardCharsets.UTF_8))

            // Read HTTP request
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")

            if (parts.size != 3) {
                sendErrorResponse(writer, 400, "Invalid HTTP request")
                return
            }

            val method = parts[0]
            val path = parts[1]

            // Read headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                val headerParts = line!!.split(":", limit = 2)
                if (headerParts.size == 2) {
                    headers[headerParts[0].trim().lowercase()] = headerParts[1].trim()
                }
            }

            // Check authentication
            val authHeader = headers[AUTH_HEADER.lowercase()]
            if (!authManager.validateToken(authHeader)) {
                sendErrorResponse(writer, 401, "Unauthorized: Invalid or missing bearer token")
                return
            }

            // Route the request
            routeRequest(writer, method, path, reader, headers)

        } catch (e: Exception) {
            Timber.e("Error handling client: $e")
            try {
                socket.close()
            } catch (e: Exception) {
                Timber.e("Error closing socket: $e")
            }
        }
    }

    /**
     * Route API requests to appropriate handlers
     */
    private suspend fun routeRequest(
        writer: BufferedWriter,
        method: String,
        path: String,
        reader: BufferedReader,
        headers: Map<String, String>
    ) {
        val route = ApiRoutes.match(method, path)
        if (route == null) {
            sendErrorResponse(writer, 404, "Endpoint not found: $method $path")
            return
        }

        val requestContext = ApiRequestContext(
            writer = writer,
            reader = reader,
            sendJsonResponse = ::sendJsonResponse,
            sendErrorResponse = ::sendErrorResponse,
            readPayload = ::readPayload,
            multipartReader = ::readMultipartForm,
            contentType = headers["content-type"] ?: ""
        )

        val controller = controllers.firstOrNull { it.supports(route) }
        if (controller != null) {
            controller.handle(route, requestContext)
            return
        }

        when (route) {
            // GET /api/stats - Get server statistics
            is ApiRoutes.Match.GetStats -> handleGetStats(writer)

            // GET /api/health - Health check
            is ApiRoutes.Match.HealthCheck -> handleHealthCheck(writer)

            else -> sendErrorResponse(writer, 404, "Endpoint not found: $method $path")
        }
    }

    /**
    * Handle GET /api/stats - Get server statistics
     */
    private suspend fun handleGetStats(writer: BufferedWriter) {
        try {
            val stats = taskScheduler.getTaskStatistics()
            sendJsonResponse(writer, 200, stats)
        } catch (e: Exception) {
            Timber.e("Error getting stats: $e")
            sendErrorResponse(writer, 500, "Failed to get statistics")
        }
    }

    /**
    * Handle GET /api/health - Health check
     */
    private suspend fun handleHealthCheck(writer: BufferedWriter) {
        try {
            val response = mapOf(
                "status" to "healthy",
                "timestamp" to System.currentTimeMillis(),
                "python_initialized" to pythonRuntimeManager.isPythonInitialized()
            )
            sendJsonResponse(writer, 200, response)
        } catch (e: Exception) {
            Timber.e("Error during health check: $e")
            sendErrorResponse(writer, 500, "Health check failed")
        }
    }

    /**
     * Send JSON response
     */
    private fun sendJsonResponse(writer: BufferedWriter, statusCode: Int, data: Any) {
        val json = com.google.gson.Gson().toJson(data)
        val response = buildHttpResponse(statusCode, CONTENT_TYPE_JSON, json)
        writer.write(response)
        writer.flush()
    }

    /**
     * Send error response
     */
    private fun sendErrorResponse(writer: BufferedWriter, statusCode: Int, message: String) {
        val errorResponse = ErrorResponse(
            errorCode = statusCode.toString(),
            message = message
        )
        sendJsonResponse(writer, statusCode, errorResponse)
    }

    /**
     * Build HTTP response
     */
    private fun buildHttpResponse(statusCode: Int, contentType: String, body: String): String {
        val statusMessage = when (statusCode) {
            200 -> "OK"
            201 -> "Created"
            202 -> "Accepted"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            409 -> "Conflict"
            500 -> "Internal Server Error"
            else -> "Unknown"
        }

        return """
HTTP/1.1 $statusCode $statusMessage
Content-Type: $contentType; charset=utf-8
Content-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}
Connection: close

$body
""".trimIndent()
    }

    /**
     * Read HTTP payload
     */
    private fun readPayload(reader: BufferedReader): String {
        val contentLengthLine = reader.readLine()
        val contentLength = contentLengthLine?.toIntOrNull() ?: 0

        val maxPayloadSize = appPreferences.maxPayloadSizeBytes()
        if (contentLength == 0 || contentLength > maxPayloadSize) {
            throw IllegalArgumentException("Invalid or oversized payload")
        }

        val buffer = CharArray(contentLength)
        reader.read(buffer)
        return String(buffer)
    }

    /**
     * Parse a multipart/form-data body into a map of field name to field value.
     */
    private fun readMultipartForm(reader: BufferedReader, boundary: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val delimiter = "--$boundary"
        val terminator = "--$boundary--"

        // Consume preamble until the first boundary marker
        var line: String? = reader.readLine()
        if (line == null) return fields
        while (line != null && line.trim() != delimiter) {
            line = reader.readLine()
        }
        if (line == null) return fields

        while (true) {
            // Read part headers and capture the field name
            val name = readMultipartFieldName(reader) ?: break
            // Read field content until the next boundary or the terminator
            val content = StringBuilder()
            line = reader.readLine() ?: break
            while (line != null && line.trim() != delimiter && line.trim() != terminator) {
                content.append(line).append('\n')
                line = reader.readLine()
            }
            fields[name] = content.toString().trimEnd()
            if (line == null || line.trim() == terminator) break
        }
        return fields
    }

    /**
     * Read multipart part headers and return the field name, or null on EOF.
     */
    private fun readMultipartFieldName(reader: BufferedReader): String? {
        var name: String? = null
        var line: String? = reader.readLine()
        while (line != null && line.isNotEmpty()) {
            if (line.startsWith("Content-Disposition:", ignoreCase = true)) {
                val marker = "name=\""
                val nameIndex = line.indexOf(marker)
                if (nameIndex >= 0) {
                    val start = nameIndex + marker.length
                    val end = line.indexOf('"', start)
                    if (end > start) {
                        name = line.substring(start, end)
                    }
                }
            }
            line = reader.readLine()
        }
        return name
    }

}
