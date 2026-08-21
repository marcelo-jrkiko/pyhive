package krs.pyhive

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent
import krs.pyhive.models.TaskStatus
import krs.pyhive.settings.SettingsActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.NetworkInterface

/**
 * Main Activity for Python Task Runner
 * Displays a grid of running tasks with auto-refresh every 5 seconds.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REFRESH_INTERVAL_MS = 5_000L
        private const val GRID_COLUMNS = 2
    }

    private lateinit var taskGrid: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var serverInfoBar: LinearLayout
    private lateinit var serverStatusDot: View
    private lateinit var serverInfoText: TextView
    private lateinit var taskCountBadge: TextView
    private lateinit var taskGridAdapter: TaskGridAdapter

    private var refreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        taskGrid = findViewById(R.id.task_grid)
        emptyState = findViewById(R.id.empty_state)
        serverInfoBar = findViewById(R.id.server_info_bar)
        serverStatusDot = findViewById(R.id.server_status_dot)
        serverInfoText = findViewById(R.id.server_info_text)
        taskCountBadge = findViewById(R.id.task_count_badge)

        // Settings button
        findViewById<Button>(R.id.open_settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Setup task grid
        taskGridAdapter = TaskGridAdapter(
            onLogsClick = { task ->
                val intent = Intent(this, TaskLogsActivity::class.java).apply {
                    putExtra("task_id", task.taskId)
                    putExtra("script_name", task.scriptName)
                }
                startActivity(intent)
            }
        )
        taskGrid.apply {
            layoutManager = GridLayoutManager(this@MainActivity, GRID_COLUMNS)
            adapter = taskGridAdapter
        }

        // Initial refresh
        refreshTaskGrid()
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                refreshTaskGrid()
            }
        }
    }

    private fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun refreshTaskGrid() {
        try {
            val taskScheduler = PyHiveApp.getTaskScheduler()
            val authManager = PyHiveApp.getAuthManager()
            val appPreferences = PyHiveApp.getAppPreferences()
            val pythonRuntimeManager = PyHiveApp.getPythonRuntimeManager()
            val deviceIp = getDeviceIpAddress()
            val serverRunning = PyHiveApp.getInstance().isApiServerRunning()

            // Update server info bar
            updateServerInfoBar(serverRunning, deviceIp)

            // Get running tasks
            val runningTasks = taskScheduler.getTasksByStatus(TaskStatus.RUNNING)

            // Update grid
            if (runningTasks.isEmpty()) {
                taskGrid.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
            } else {
                emptyState.visibility = View.GONE
                taskGrid.visibility = View.VISIBLE
                taskGridAdapter.updateTasks(runningTasks)
            }

            // Update task count badge
            taskCountBadge.text = getString(R.string.task_count_format, runningTasks.size)

            Timber.d("Grid refreshed: ${runningTasks.size} running tasks")
        } catch (e: Exception) {
            Timber.e("Error refreshing task grid: $e")
        }
    }

    private fun updateServerInfoBar(serverRunning: Boolean, deviceIp: String) {
        serverInfoBar.visibility = View.VISIBLE

        if (serverRunning) {
            serverStatusDot.setBackgroundResource(R.drawable.circle_green)
            val port = PyHiveApp.getApiService().getPort()
            serverInfoText.text = getString(R.string.server_status_running, port, deviceIp)
        } else {
            serverStatusDot.setBackgroundResource(R.drawable.circle_red)
            serverInfoText.text = getString(R.string.server_status_stopped)
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
