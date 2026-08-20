package krs.pyhive.sandbox

import android.content.Context
import timber.log.Timber
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import krs.pyhive.utils.StringUtils

/**
 * Manages file system sandboxing for Python tasks
 * Each task gets an isolated directory where it can only read/write
 */
class SandboxManager(private val context: Context) {

    companion object {
        private const val SANDBOX_BASE_DIR = "python_sandboxes"
        private const val MAX_SANDBOX_SIZE_MB = 100 // 100MB per sandbox
        private const val FILE_ACCESS_RESTRICTION_ASSET = "python/file_access_restrictions.py"
        private const val SANDBOX_PATH_PLACEHOLDER = "__SANDBOX_DIR__"
        private const val CHAQUOPY_PATH_PLACEHOLDER = "__CHAQUOPY_DIR__"
    }

    private val sandboxPaths = ConcurrentHashMap<String, File>()

    /**
     * Create a new sandbox directory for a task
     */
    fun createSandbox(taskId: String): File {
        val sandboxDir = File(
            context.getExternalFilesDir(null),
            "$SANDBOX_BASE_DIR/$taskId"
        )

        if (!sandboxDir.exists()) {
            val created = sandboxDir.mkdirs()
            if (!created) {
                throw RuntimeException("Failed to create sandbox directory for task $taskId")
            }
        }

        sandboxPaths[taskId] = sandboxDir
        Timber.d("Created sandbox for task $taskId at ${sandboxDir.absolutePath}")
        return sandboxDir
    }

    /**
     * Get the sandbox directory for a task
     */
    fun getSandboxDir(taskId: String): File? {
        return sandboxPaths.getOrPut(taskId) {
            val sandboxDir = File(
                context.getExternalFilesDir(null),
                "$SANDBOX_BASE_DIR/$taskId"
            )
            if (sandboxDir.exists()) sandboxDir else null
        }
    }

    /**
     * Verify that a file path is within the sandbox
     */
    fun isPathInSandbox(taskId: String, filePath: String): Boolean {
        val sandbox = getSandboxDir(taskId) ?: return false
        val file = File(filePath)

        return try {
            val sandboxCanonical: Path = sandbox.canonicalFile.toPath().normalize()
            val fileCanonical: Path = file.canonicalFile.toPath().normalize()
            fileCanonical.startsWith(sandboxCanonical)
        } catch (e: Exception) {
            Timber.e("Error checking if path is in sandbox: $e")
            false
        }
    }

    /**
     * Get sandbox size in bytes
     */
    fun getSandboxSize(taskId: String): Long {
        val sandbox = getSandboxDir(taskId) ?: return 0
        return calculateDirSize(sandbox)
    }

    /**
     * Check if sandbox has exceeded size limit
     */
    fun isSandboxSizeExceeded(taskId: String): Boolean {
        val sizeBytes = getSandboxSize(taskId)
        val maxBytes = MAX_SANDBOX_SIZE_MB * 1024 * 1024
        return sizeBytes > maxBytes
    }

    /**
     * Clean up sandbox directory
     */
    fun deleteSandbox(taskId: String) {
        val sandbox = getSandboxDir(taskId) ?: return

        try {
            sandbox.deleteRecursively()
            sandboxPaths.remove(taskId)
            Timber.d("Deleted sandbox for task $taskId")
        } catch (e: Exception) {
            Timber.e("Error deleting sandbox for task $taskId: $e")
        }
    }

    /**
     * Clean up all old sandboxes
     */
    fun cleanupOldSandboxes(ageMillis: Long = 7 * 24 * 60 * 60 * 1000) { // 7 days default
        val sandboxBase = File(
            context.getExternalFilesDir(null),
            SANDBOX_BASE_DIR
        )

        if (!sandboxBase.exists()) return

        val currentTime = System.currentTimeMillis()
        sandboxBase.listFiles()?.forEach { sandbox ->
            if (sandbox.isDirectory) {
                val age = currentTime - sandbox.lastModified()
                if (age > ageMillis) {
                    try {
                        sandbox.deleteRecursively()
                        sandboxPaths.remove(sandbox.name)
                        Timber.d("Cleaned up old sandbox: ${sandbox.name}")
                    } catch (e: Exception) {
                        Timber.e("Error cleaning up sandbox ${sandbox.name}: $e")
                    }
                }
            }
        }
    }

    /**
     * Calculate directory size recursively
     */
    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirSize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    /**
     * Get all active sandboxes
     */
    fun getAllSandboxes(): Map<String, File> {
        return sandboxPaths.toMap()
    }

    /**
     * Create a Python module that enforces file access restrictions
     */
    fun getFileAccessRestrictionModule(taskId: String): String {
        val sandboxDir = getSandboxDir(taskId)?.absolutePath ?: ""
        val chaquopyDir = File(context.filesDir, "chaquopy").absolutePath
        val scriptTemplate = try {
            context.assets
                .open(FILE_ACCESS_RESTRICTION_ASSET)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load file access restriction asset")
            throw IllegalStateException("Unable to load file access restriction module", e)
        }

        return scriptTemplate
            .replace(SANDBOX_PATH_PLACEHOLDER, StringUtils.escapePythonString(sandboxDir))
            .replace(CHAQUOPY_PATH_PLACEHOLDER, StringUtils.escapePythonString(chaquopyDir))
    }

}

/**
 * Exception thrown when a task tries to access files outside its sandbox
 */
class SandboxViolationException(message: String) : SecurityException(message)
