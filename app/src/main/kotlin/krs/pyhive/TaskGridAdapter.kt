package krs.pyhive

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import krs.pyhive.models.PythonTask
import krs.pyhive.models.TaskStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * RecyclerView adapter for displaying tasks in a grid.
 * Each item shows a card with task name, ID, status badge, and timing info.
 */
class TaskGridAdapter(
    private var tasks: List<PythonTask> = emptyList()
) : RecyclerView.Adapter<TaskGridAdapter.TaskViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun updateTasks(newTasks: List<PythonTask>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task_card, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
    }

    override fun getItemCount(): Int = tasks.size

    inner class TaskViewHolder(
        private val cardView: com.google.android.material.card.MaterialCardView
    ) : RecyclerView.ViewHolder(cardView) {

        private val statusIndicator: android.view.View =
            cardView.findViewById(R.id.status_indicator)
        private val taskName: android.widget.TextView =
            cardView.findViewById(R.id.task_name)
        private val taskId: android.widget.TextView =
            cardView.findViewById(R.id.task_id)
        private val statusBadge: android.widget.TextView =
            cardView.findViewById(R.id.status_badge)
        private val taskTime: android.widget.TextView =
            cardView.findViewById(R.id.task_time)

        fun bind(task: PythonTask) {
            val context = cardView.context
            val status = try {
                TaskStatus.valueOf(task.status)
            } catch (_: IllegalArgumentException) {
                TaskStatus.PENDING
            }
            val statusColor = getStatusColor(context, status)

            // Set task name (script name, fallback to ID)
            taskName.text = task.scriptName.ifBlank {
                task.taskId.take(12) + "..."
            }

            // Set truncated task ID
            taskId.text = if (task.taskId.length > 20) {
                task.taskId.take(10) + "..." + task.taskId.takeLast(8)
            } else {
                task.taskId
            }

            // Status indicator bar color
            statusIndicator.setBackgroundColor(statusColor)

            // Status badge
            statusBadge.text = status.name
            statusBadge.setBackgroundColor(statusColor)

            // Time info
            taskTime.text = buildTimeInfo(task, status)

            // Card stroke color for running tasks
            if (status == TaskStatus.RUNNING) {
                cardView.strokeWidth = 2
                cardView.strokeColor = statusColor
            } else {
                cardView.strokeWidth = 0
            }
        }

        private fun getStatusColor(context: android.content.Context, status: TaskStatus): Int {
            return when (status) {
                TaskStatus.RUNNING -> ContextCompat.getColor(context, R.color.status_running)
                TaskStatus.COMPLETED -> ContextCompat.getColor(context, R.color.status_completed)
                TaskStatus.FAILED -> ContextCompat.getColor(context, R.color.status_failed)
                TaskStatus.PENDING -> ContextCompat.getColor(context, R.color.status_pending)
                TaskStatus.SCHEDULED -> ContextCompat.getColor(context, R.color.status_scheduled)
                TaskStatus.CANCELLED -> ContextCompat.getColor(context, R.color.status_cancelled)
            }
        }

        private fun buildTimeInfo(task: PythonTask, status: TaskStatus): String {
            return when (status) {
                TaskStatus.RUNNING -> {
                    val startedAt = task.startedAt
                    if (startedAt != null && startedAt > 0) {
                        "Running since ${dateFormat.format(Date(startedAt))}"
                    } else {
                        "Running..."
                    }
                }
                TaskStatus.COMPLETED, TaskStatus.FAILED -> {
                    val execTime = task.executionTime
                    val timeStr = if (execTime > 0) {
                        formatDuration(execTime)
                    } else {
                        null
                    }
                    val completedAt = task.completedAt
                    val dateStr = if (completedAt != null && completedAt > 0) {
                        dateFormat.format(Date(completedAt))
                    } else {
                        null
                    }
                    listOfNotNull(timeStr, dateStr).joinToString(" • ")
                }
                TaskStatus.SCHEDULED -> {
                    val scheduledTime = task.scheduledTime
                    if (scheduledTime != null && scheduledTime > 0) {
                        "Scheduled: ${dateFormat.format(Date(scheduledTime))}"
                    } else {
                        "Scheduled"
                    }
                }
                TaskStatus.PENDING -> {
                    "Created: ${dateFormat.format(Date(task.createdAt))}"
                }
                TaskStatus.CANCELLED -> {
                    val completedAt = task.completedAt
                    if (completedAt != null && completedAt > 0) {
                        "Cancelled: ${dateFormat.format(Date(completedAt))}"
                    } else {
                        "Cancelled"
                    }
                }
            }
        }

        private fun formatDuration(millis: Long): String {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millis)
            return if (seconds < 60) {
                "${seconds}s"
            } else {
                val minutes = seconds / 60
                val secs = seconds % 60
                "${minutes}m ${secs}s"
            }
        }
    }
}