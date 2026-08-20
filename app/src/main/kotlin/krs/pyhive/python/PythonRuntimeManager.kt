package krs.pyhive.python

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.gson.Gson
import krs.pyhive.sandbox.SandboxManager
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Manages Python runtime integration and script execution
 */
class PythonRuntimeManager(
    private val context: Context,
    private val sandboxManager: SandboxManager
) {
    private val gson = Gson()
    private val executionLock = ReentrantLock()
    private val workerScriptSource by lazy { readAssetText(WORKER_SCRIPT_ASSET) }

    companion object {
        private const val WORKER_SCRIPT_ASSET = "python/task_worker.py"
    }

    /**
     * Initialize Python runtime
     */
    fun initializePython() {
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
                Timber.d("Python runtime initialized successfully")
            }
        } catch (e: Exception) {
            Timber.e("Failed to initialize Python runtime: $e")
            throw RuntimeException("Python initialization failed", e)
        }
    }

    /**
     * Execute a Python script with sandbox restrictions
     */
    fun executePythonScript(
        taskId: String,
        scriptContent: String,
        argsJson: String = "{}",
        timeoutSeconds: Long = 300
    ): ScriptExecutionResult {
        return try {
            initializePython()

            val python = Python.getInstance()
            val sandboxDir = sandboxManager.getSandboxDir(taskId)
                ?: sandboxManager.createSandbox(taskId)

            // Persist the user script as a module source file inside the sandbox.
            val userScriptFile = File(sandboxDir, "user_script.py")
            userScriptFile.writeText(scriptContent)

            val params = WorkerTaskParams(
                taskId = taskId,
                scriptContent = scriptContent,
                sandboxDir = sandboxDir.absolutePath,
                userScriptPath = userScriptFile.absolutePath,
                restrictionModule = sandboxManager.getFileAccessRestrictionModule(taskId),
                argsJson = argsJson.ifBlank { "{}" }
            )

            val result = executeWorkerProcess(
                taskId = taskId,
                python = python,
                params = params,
                timeoutSeconds = timeoutSeconds
            )

            ScriptExecutionResult(
                taskId = taskId,
                success = result.success,
                result = result.result,
                error = result.error,
                executionTimeMs = result.executionTimeMs,
                output = buildString {
                    if (result.stdout.isNotBlank()) {
                        append(result.stdout)
                    }
                    if (result.stderr.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(result.stderr)
                    }
                }
            )
        } catch (e: TimeoutException) {
            Timber.e("Script execution timeout for task $taskId: $e")
            ScriptExecutionResult(
                taskId = taskId,
                success = false,
                error = "Script execution exceeded timeout of $timeoutSeconds seconds",
                executionTimeMs = timeoutSeconds * 1000
            )
        } catch (e: Exception) {
            Timber.e("Error executing Python script for task $taskId: $e")
            ScriptExecutionResult(
                taskId = taskId,
                success = false,
                error = e.message ?: "Unknown error during script execution"
            )
        }
    }

    /**
     * Execute task inside an isolated in-process worker task.
     */
    private fun executeWorkerProcess(
        taskId: String,
        python: Python,
        params: WorkerTaskParams,
        timeoutSeconds: Long
    ): WorkerProcessResult {
        return executionLock.withLock {
            val paramsJson = gson.toJson(params)

            var workerResultJson = ""
            var workerError: String? = null

            val executionThread = Thread(
                {
                    try {
                        val workerGlobals = python.builtins.callAttr("dict")
                        workerGlobals.callAttr("__setitem__", "__name__", "__chaquopy_task_worker__")
                        workerGlobals.callAttr("__setitem__", "__builtins__", python.builtins)

                        // Load worker source from assets to avoid import path dependency.
                        python.builtins.callAttr("exec", workerScriptSource, workerGlobals, workerGlobals)

                        val runTask = workerGlobals.callAttr("__getitem__", "run_task")
                        val resultObj = runTask.call(paramsJson)
                        workerResultJson = resultObj.toString()
                    } catch (e: Exception) {
                        workerError = e.message ?: e.toString()
                    }
                },
                "py-worker-$taskId"
            )

            executionThread.start()
            executionThread.join(timeoutSeconds * 1000)

            if (executionThread.isAlive) {
                executionThread.interrupt()
                throw TimeoutException("Script execution exceeded $timeoutSeconds seconds")
            }

            if (!workerError.isNullOrBlank()) {
                throw RuntimeException("Worker task failed: $workerError")
            }

            if (workerResultJson.isBlank()) {
                throw RuntimeException("Worker task returned an empty result")
            }

            gson.fromJson(workerResultJson, WorkerProcessResult::class.java)
                ?: throw RuntimeException("Failed to parse worker result payload")
        }
    }

    private fun File.writeText(content: String) {
        parentFile?.mkdirs()
        OutputStreamWriter(FileOutputStream(this), StandardCharsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }

    private fun readAssetText(assetPath: String): String {
        return context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }

    /**
     * Stop Python runtime
     */
    fun stopPython() {
        try {
            if (Python.isStarted()) {
                // Python.getInstance().shutdown() - Not typically called in Android
                Timber.d("Python runtime stopped")
            }
        } catch (e: Exception) {
            Timber.e("Error stopping Python runtime: $e")
        }
    }

    /**
     * Check if Python is initialized
     */
    fun isPythonInitialized(): Boolean = Python.isStarted()
}

/**
 * Result of Python script execution
 */
data class ScriptExecutionResult(
    val taskId: String,
    val success: Boolean,
    val result: String = "",
    val error: String = "",
    val executionTimeMs: Long = 0,
    val output: String = ""
)

private data class WorkerTaskParams(
    val taskId: String,
    val scriptContent: String,
    val sandboxDir: String,
    val userScriptPath: String,
    val restrictionModule: String,
    val argsJson: String
)

private data class WorkerProcessResult(
    val success: Boolean = false,
    val result: String = "",
    val error: String = "",
    val stdout: String = "",
    val stderr: String = "",
    val executionTimeMs: Long = 0
)
