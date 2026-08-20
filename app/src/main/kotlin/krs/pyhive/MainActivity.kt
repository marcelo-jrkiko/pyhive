package krs.pyhive

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.widget.Button
import android.widget.TextView
import krs.pyhive.settings.SettingsActivity
import timber.log.Timber
import java.net.NetworkInterface

/**
 * Main Activity for Python Task Runner
 * Displays API status and server information
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.open_settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateUIWithServerInfo()
    }

    override fun onResume() {
        super.onResume()
        updateUIWithServerInfo()
    }

    private fun updateUIWithServerInfo() {
        try {
            val authManager = PyHiveApp.getAuthManager()
            val taskScheduler = PyHiveApp.getTaskScheduler()
            val pythonRuntimeManager = PyHiveApp.getPythonRuntimeManager()
            val appPreferences = PyHiveApp.getAppPreferences()
            val stats = taskScheduler.getTaskStatistics()
            val deviceIp = getDeviceIpAddress()
            val serverRunning = PyHiveApp.getInstance().isApiServerRunning()
            val displayedToken = authManager.getToken()?.let { token ->
                if (appPreferences.showFullToken()) token else "${token.take(20)}..."
            } ?: "No token"

            val infoText = """
                === PyHive API ===
                
                Status: ${if (serverRunning) "Running" else "Stopped"}
                Server Port: ${PyHiveApp.getApiService().getPort()}
                Device IP: $deviceIp
                
                API Token: $displayedToken
                Python Initialized: ${pythonRuntimeManager.isPythonInitialized()}
                
                === Task Statistics ===
                Total Tasks: ${stats.totalTasks}
                Pending: ${stats.pendingTasks}
                Running: ${stats.runningTasks}
                Completed: ${stats.completedTasks}
                Failed: ${stats.failedTasks}
                Cancelled: ${stats.cancelledTasks}
                Scheduled: ${stats.scheduledTasks}
            """.trimIndent()

            val statusTextView: TextView? = findViewById(R.id.status_text)
            statusTextView?.text = infoText

            Timber.d("UI updated with server information")
        } catch (e: Exception) {
            Timber.e("Error updating UI: $e")
        }
    }

    private fun getDeviceIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val network = interfaces.nextElement()
                val addresses = network.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    val hostAddress = address.hostAddress ?: continue
                    if (!address.isLoopbackAddress && hostAddress.contains(".")) {
                        return hostAddress
                    }
                }
            }
            "Not Connected"
        } catch (e: Exception) {
            Timber.e("Error getting device IP: $e")
            "Unknown"
        }
    }
}
