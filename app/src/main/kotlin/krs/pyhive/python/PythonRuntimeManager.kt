package krs.pyhive.python

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.gson.Gson
import krs.pyhive.preferences.AppPreferences
import krs.pyhive.sandbox.SandboxManager
import krs.pyhive.utils.AssetUtils
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Manages Python runtime integration and script execution
 */
class PythonRuntimeManager(
    private val context: Context,
    private val sandboxManager: SandboxManager,
    private val appPreferences: AppPreferences
) {
    private val gson = Gson()
    private val executionLock = ReentrantLock()
    private val workerScriptSource by lazy { readAssetText(WORKER_SCRIPT_ASSET) }
    private val listPackagesScriptSource by lazy { readAssetText(LIST_PACKAGES_SCRIPT_ASSET) }

    companion object {
        private const val WORKER_SCRIPT_ASSET = "python/task_worker.py"
        private const val LIST_PACKAGES_SCRIPT_ASSET = "python/list_installed_packages.py"

        /** How often the memory watchdog samples the JVM heap (ms). */
        private const val MEMORY_SAMPLE_INTERVAL_MS = 100L

        /**
         * Consecutive over-limit samples required before the worker is killed,
         * giving the GC a chance to reclaim transient heap spikes.
         */
        private const val MEMORY_OVERRUN_SAMPLES = 5
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

    fun getInstalledPackages(): List<Map<String, String>> {
        return try {
            initializePython()
            val python = Python.getInstance()

            val workerGlobals = python.builtins.callAttr("dict")
            workerGlobals.callAttr("__setitem__", "__name__", "__chaquopy_code__")
            workerGlobals.callAttr("__setitem__", "__builtins__", python.builtins)

            val resultJson = python.builtins.callAttr("exec", listPackagesScriptSource, workerGlobals, workerGlobals)
            val getInstalledPackages = workerGlobals.callAttr("__getitem__", "get_installed_packages")
            val packagesJson = getInstalledPackages.call()

            @Suppress("UNCHECKED_CAST")
            return gson.fromJson(packagesJson.toString(), Array<Any>::class.java).map { it as Map<String, String> }.toList()
        } catch (e: Exception) {
            Timber.e("Error retrieving installed packages: $e")
            emptyList()
        }
    }

    /**
     * Execute a Python script with sandbox restrictions.
     *
     * The maximum amount of JVM heap a task may use comes from
     * [AppPreferences.maxTaskMemoryBytes()]; set `max task memory` to 0 in Settings to disable the watchdog.
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
     *
     * The worker runs on its own thread and is bounded by:
     *  - [timeoutSeconds]: hard wall-clock limit (existing behavior);
     *  - memory limit: a watchdog samples the JVM heap (limit read from
     *    [AppPreferences.maxTaskMemoryBytes()]) and interrupts the worker when
     *    usage stays above the limit across consecutive samples. Note this
     *    bounds JVM-managed allocations only; Chaquopy's native (C/Python)
     *    allocations are not visible to [Runtime].
     */
    private fun executeWorkerProcess(
        taskId: String,
        python: Python,
        params: WorkerTaskParams,
        timeoutSeconds: Long
    ): WorkerProcessResult {
        return executionLock.withLock {
            val paramsJson = gson.toJson(params)
            val maxMemoryBytes = appPreferences.maxTaskMemoryBytes()

            var workerResultJson = ""
            val workerError = AtomicReference<String?>()
            val memoryLimitError = AtomicReference<String?>()

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
                        workerError.set(e.message ?: e.toString())
                    }
                },
                "py-worker-$taskId"
            )

            // Memory watchdog: samples the process JVM heap and interrupts the
            // worker once usage exceeds the limit across consecutive samples,
            // so transient spikes that the GC can reclaim don't kill the task.
            val stopWatchdog = AtomicBoolean(false)
            val watchdog = if (maxMemoryBytes > 0) {
                Thread(
                    {
                        var overrunSamples = 0
                        while (!stopWatchdog.get()) {
                            if (!executionThread.isAlive) break

                            val usedBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
                            if (usedBytes > maxMemoryBytes) {
                                overrunSamples++
                                if (overrunSamples >= MEMORY_OVERRUN_SAMPLES) {
                                    val message = "${formatBytes(usedBytes)} exceeded the memory limit of ${formatBytes(maxMemoryBytes)}"
                                    memoryLimitError.set(message)
                                    Timber.w("Stopping task $taskId: $message")
                                    executionThread.interrupt()
                                    break
                                }
                            } else {
                                overrunSamples = 0
                            }
                            Thread.sleep(MEMORY_SAMPLE_INTERVAL_MS)
                        }
                    },
                    "mem-watchdog-$taskId"
                ).also {
                    it.isDaemon = true
                    it.start()
                }
            } else null

            executionThread.start()
            executionThread.join(timeoutSeconds * 1000)

            val workerStillAlive = executionThread.isAlive
            stopWatchdog.set(true)
            watchdog?.join(MEMORY_SAMPLE_INTERVAL_MS * 2)

            memoryLimitError.get()?.let { throw RuntimeException(it) }

            if (workerStillAlive) {
                executionThread.interrupt()
                throw TimeoutException("Script execution exceeded $timeoutSeconds seconds")
            }

            workerError.get()?.let { throw RuntimeException("Worker task failed: $it") }

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
        return AssetUtils.readAssetText(context, assetPath)
    }

    private fun formatBytes(bytes: Long): String {
        return if (bytes >= 1024 * 1024) {
            String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        } else {
            "$bytes bytes"
        }
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
