package krs.pyhive

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Activity that tails stdout / stderr log files for a specific task.
 * Auto-refreshes every 3 seconds while the task is running; stops
 * refreshing once the task reaches a terminal state.
 */
class TaskLogsActivity : AppCompatActivity() {

    companion object {
        private const val REFRESH_INTERVAL_MS = 3_000L
        private const val MAX_DISPLAY_LINES = 500
    }

    private lateinit var logsTextView: TextView
    private lateinit var logsScrollView: ScrollView
    private lateinit var headerText: TextView

    private var taskId: String = ""
    private var taskName: String = ""
    private var refreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_logs)

        taskId = intent.getStringExtra("task_id") ?: ""
        taskName = intent.getStringExtra("script_name") ?: taskId

        supportActionBar?.apply {
            title = "Logs: $taskName"
            setDisplayHomeAsUpEnabled(true)
        }

        logsTextView = findViewById(R.id.logs_text)
        logsScrollView = findViewById(R.id.logs_scroll)
        headerText = findViewById(R.id.logs_header)

        headerText.text = getString(R.string.logs_header_format, taskName, taskId)
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                refreshLogs()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun refreshLogs() {
        try {
            val outputDir = File(getExternalFilesDir(null), "task_output/$taskId")
            val stdoutFile = File(outputDir, "stdout.log")
            val stderrFile = File(outputDir, "stderr.log")

            val sb = StringBuilder()

            appendFileLines(sb, stdoutFile, "▶ STDOUT")
            appendFileLines(sb, stderrFile, "▶ STDERR")

            val text = sb.toString()
            if (text.isBlank()) {
                logsTextView.text = getString(R.string.logs_empty)
            } else {
                logsTextView.text = text
            }

            // Auto-scroll to bottom
            logsScrollView.post {
                logsScrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        } catch (e: Exception) {
            Timber.e("Error refreshing logs for task $taskId: $e")
            logsTextView.text = getString(R.string.logs_error, e.message ?: "Unknown error")
        }
    }

    private fun appendFileLines(sb: StringBuilder, file: File, label: String) {
        if (!file.exists() || !file.isFile) return

        try {
            val allLines = file.readLines()
            val lines = if (allLines.size <= MAX_DISPLAY_LINES) {
                allLines
            } else {
                allLines.takeLast(MAX_DISPLAY_LINES)
            }

            if (lines.isEmpty()) return

            sb.appendLine("$label (${lines.size} of ${allLines.size} lines)")
            sb.appendLine("─".repeat(60))
            for (line in lines) {
                sb.appendLine(line)
            }
            sb.appendLine()
        } catch (e: Exception) {
            Timber.w("Error reading log file ${file.absolutePath}: $e")
        }
    }
}