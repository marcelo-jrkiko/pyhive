package krs.pyhive

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.objectbox.BoxStore
import krs.pyhive.api.PythonTaskRunnerService
import krs.pyhive.auth.AuthenticationManager
import krs.pyhive.data.MyObjectBox
import krs.pyhive.data.TaskRepository
import krs.pyhive.preferences.AppPreferences
import krs.pyhive.python.PythonRuntimeManager
import krs.pyhive.sandbox.SandboxManager
import krs.pyhive.scheduler.TaskScheduler
import timber.log.Timber

/**
 * Application class for Python Task Runner
 * Initializes core components and services
 */
class PyHiveApp : Application() {

    companion object {
        private var instance: PyHiveApp? = null
        private var boxStore: BoxStore? = null
        private var taskRepository: TaskRepository? = null
        private var apiService: PythonTaskRunnerService? = null
        private var taskScheduler: TaskScheduler? = null
        private var authManager: AuthenticationManager? = null
        private var sandboxManager: SandboxManager? = null
        private var pythonRuntimeManager: PythonRuntimeManager? = null
        private var appPreferences: AppPreferences? = null

        fun getInstance(): PyHiveApp = instance!!
        fun getBoxStore(): BoxStore = boxStore!!
        fun getTaskRepository(): TaskRepository = taskRepository!!
        fun getApiService(): PythonTaskRunnerService = apiService!!
        fun getTaskScheduler(): TaskScheduler = taskScheduler!!
        fun getAuthManager(): AuthenticationManager = authManager!!
        fun getSandboxManager(): SandboxManager = sandboxManager!!
        fun getPythonRuntimeManager(): PythonRuntimeManager = pythonRuntimeManager!!
        fun getAppPreferences(): AppPreferences = appPreferences!!
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        // Initialize components
        initializeComponents()

        Timber.d("Application initialized")
    }

    private fun initializeComponents() {
        appPreferences = AppPreferences(this)

        // Initialize encrypted shared preferences
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            this,
            "python_task_runner_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Initialize managers
        authManager = AuthenticationManager(sharedPreferences)
        sandboxManager = SandboxManager(this)
        pythonRuntimeManager = PythonRuntimeManager(this, sandboxManager!!, appPreferences!!)

        // Initialize ObjectBox and task repository
        boxStore = MyObjectBox.builder()
            .androidContext(this)
            .build()
        taskRepository = TaskRepository(boxStore!!)

        // Initialize task scheduler
        taskScheduler = TaskScheduler(
            this,
            pythonRuntimeManager!!,
            sandboxManager!!,
            taskRepository!!
        )

        // Initialize API service
        apiService = PythonTaskRunnerService(
            this,
            authManager!!,
            taskScheduler!!,
            pythonRuntimeManager!!,
            sandboxManager!!,
            appPreferences!!,
            port = appPreferences!!.apiPort()
        )

        // Generate initial token if needed
        if (!authManager!!.hasToken()) {
            val token = authManager!!.generateNewToken()
            Timber.d("Generated initial API token: $token")
        }

        // Initialize Python runtime
        try {
            pythonRuntimeManager!!.initializePython()
        } catch (e: Exception) {
            Timber.e("Failed to initialize Python: $e")
        }

        // Start API server based on preferences
        if (appPreferences!!.autoStartServer()) {
            apiService!!.start()
        } else {
            Timber.d("API server auto-start disabled")
        }

        // Cleanup old tasks and sandboxes on app start
        val cleanupAgeMillis = appPreferences!!.cleanupAgeMillis()
        taskScheduler!!.cleanupOldTasks(cleanupAgeMillis)
        sandboxManager!!.cleanupOldSandboxes(cleanupAgeMillis)
    }

    fun isApiServerRunning(): Boolean = apiService?.isRunning() == true

    fun startApiServerIfNeeded() {
        if (apiService == null) return
        if (apiService!!.isRunning()) return
        apiService!!.start()
    }

    fun stopApiServer() {
        if (apiService == null) return
        if (!apiService!!.isRunning()) return
        apiService!!.stop()
    }

    fun restartApiServer() {
        stopApiServer()
        apiService = PythonTaskRunnerService(
            this,
            authManager!!,
            taskScheduler!!,
            pythonRuntimeManager!!,
            sandboxManager!!,
            appPreferences!!,
            port = appPreferences!!.apiPort()
        )

        if (appPreferences!!.autoStartServer()) {
            apiService!!.start()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // Cleanup resources
        taskScheduler?.shutdown()
        apiService?.stop()
        pythonRuntimeManager?.stopPython()
    }

    /**
     * Release tree for production logging (silent)
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Silent in production
        }
    }
}
