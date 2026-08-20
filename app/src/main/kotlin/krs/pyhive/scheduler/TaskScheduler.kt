package krs.pyhive.scheduler

import android.content.Context
import androidx.work.*
import krs.pyhive.models.PythonTask
import krs.pyhive.models.TaskStatus
import krs.pyhive.python.PythonRuntimeManager
import krs.pyhive.sandbox.SandboxManager
import timber.log.Timber
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Manages task scheduling, execution, and lifecycle
 * Handles concurrent and scheduled task execution
 */
class TaskScheduler(
    private val context: Context,
    private val pythonRuntimeManager: PythonRuntimeManager,
    private val sandboxManager: SandboxManager
) {

    companion object {
        private const val TASK_WORK_TAG = "python_task"
        private const val MAX_CONCURRENT_TASKS = 4
    }

    private val executorService: ScheduledExecutorService =
        Executors.newScheduledThreadPool(MAX_CONCURRENT_TASKS)

    private val taskRegistry = ConcurrentHashMap<String, PythonTask>()
    private val taskListeners = mutableListOf<TaskStatusListener>()

    /**
     * Submit a task for immediate execution or scheduling
     */
    fun submitTask(task: PythonTask): String {
        taskRegistry[task.taskId] = task

        return if (task.scheduledTime != null && task.scheduledTime!! > System.currentTimeMillis()) {
            // Schedule for later
            scheduleTask(task)
            task.taskId
        } else {
            // Execute immediately
            executeTask(task)
            task.taskId
        }
    }

    /**
     * Execute a task immediately
     */
    private fun executeTask(task: PythonTask) {
        executorService.execute {
            try {
                updateTaskStatus(task.taskId, TaskStatus.RUNNING)

                // Create sandbox for task
                sandboxManager.createSandbox(task.taskId)

                // Execute Python script
                val result = pythonRuntimeManager.executePythonScript(
                    taskId = task.taskId,
                    scriptContent = task.scriptContent,
                    argsJson = task.argsJson,
                    timeoutSeconds = task.timeoutSeconds
                )

                // Update task with results
                val updatedTask = taskRegistry[task.taskId]?.copy(
                    status = if (result.success) TaskStatus.COMPLETED.name else TaskStatus.FAILED.name,
                    result = result.result,
                    error = result.error,
                    output = result.output,
                    executionTime = result.executionTimeMs,
                    completedAt = System.currentTimeMillis(),
                    startedAt = task.startedAt ?: System.currentTimeMillis()
                ) ?: return@execute

                taskRegistry[task.taskId] = updatedTask
                updateTaskStatus(
                    task.taskId,
                    if (result.success) TaskStatus.COMPLETED else TaskStatus.FAILED
                )

                if (result.error.isNotEmpty()) {
                    Timber.e("Task ${task.taskId} failed with error: ${result.error}")
                } else {
                    Timber.d("Task ${task.taskId} completed successfully")
                }

                Timber.d("Task ${task.taskId} completed: ${result.output}")
            } catch (e: Exception) {
                Timber.e("Error executing task ${task.taskId}: $e")
                val failedTask = taskRegistry[task.taskId]?.copy(
                    status = TaskStatus.FAILED.name,
                    error = e.message ?: "Unknown error",
                    completedAt = System.currentTimeMillis(),
                    startedAt = task.startedAt ?: System.currentTimeMillis()
                ) ?: return@execute

                taskRegistry[task.taskId] = failedTask
                updateTaskStatus(task.taskId, TaskStatus.FAILED)
            }
        }
    }

    /**
     * Schedule a task for future execution
     */
    private fun scheduleTask(task: PythonTask) {
        val delayMs = (task.scheduledTime!! - System.currentTimeMillis()).coerceAtLeast(0)

        executorService.schedule({
            val currentTask = taskRegistry[task.taskId] ?: return@schedule
            executeTask(currentTask)
        }, delayMs, TimeUnit.MILLISECONDS)

        updateTaskStatus(task.taskId, TaskStatus.SCHEDULED)
        Timber.d("Task ${task.taskId} scheduled for ${delayMs}ms from now")
    }

    /**
     * Get task by ID
     */
    fun getTask(taskId: String): PythonTask? = taskRegistry[taskId]

    /**
     * Get all tasks
     */
    fun getAllTasks(): List<PythonTask> = taskRegistry.values.toList()

    /**
     * Get tasks by status
     */
    fun getTasksByStatus(status: TaskStatus): List<PythonTask> {
        return taskRegistry.values.filter { it.status == status.name }
    }

    /**
     * Get tasks by tag
     */
    fun getTasksByTag(tag: String): List<PythonTask> {
        return taskRegistry.values.filter { it.tags.contains(tag) }
    }

    /**
     * Cancel a task
     */
    fun cancelTask(taskId: String): Boolean {
        val task = taskRegistry[taskId] ?: return false

        return when (TaskStatus.valueOf(task.status)) {
            TaskStatus.PENDING, TaskStatus.SCHEDULED -> {
                val cancelledTask = task.copy(
                    status = TaskStatus.CANCELLED.name,
                    completedAt = System.currentTimeMillis()
                )
                taskRegistry[taskId] = cancelledTask
                updateTaskStatus(taskId, TaskStatus.CANCELLED)
                sandboxManager.deleteSandbox(taskId)
                Timber.d("Task $taskId cancelled")
                true
            }
            TaskStatus.RUNNING -> {
                Timber.d("Cannot cancel task $taskId (already running)")
                false
            }
            else -> false
        }
    }

    /**
     * Reschedule a task
     */
    fun rescheduleTask(taskId: String, newScheduledTime: Long): Boolean {
        val task = taskRegistry[taskId] ?: return false

        return when (TaskStatus.valueOf(task.status)) {
            TaskStatus.PENDING, TaskStatus.SCHEDULED -> {
                val rescheduledTask = task.copy(scheduledTime = newScheduledTime)
                taskRegistry[taskId] = rescheduledTask
                scheduleTask(rescheduledTask)
                Timber.d("Task $taskId rescheduled for $newScheduledTime")
                true
            }
            else -> {
                Timber.d("Cannot reschedule task $taskId (not in pending/scheduled state)")
                false
            }
        }
    }

    /**
     * Retry a failed task
     */
    fun retryTask(taskId: String): Boolean {
        val task = taskRegistry[taskId] ?: return false

        return if (task.retryCount < task.maxRetries && task.status == TaskStatus.FAILED.name) {
            val retriedTask = task.copy(
                retryCount = task.retryCount + 1,
                status = TaskStatus.PENDING.name,
                error = "",
                output = ""
            )
            taskRegistry[taskId] = retriedTask
            executeTask(retriedTask)
            Timber.d("Task $taskId retried (attempt ${retriedTask.retryCount})")
            true
        } else {
            false
        }
    }

    /**
     * Delete a task and its sandbox
     */
    fun deleteTask(taskId: String): Boolean {
        return if (taskRegistry.remove(taskId) != null) {
            sandboxManager.deleteSandbox(taskId)
            Timber.d("Task $taskId deleted")
            true
        } else {
            false
        }
    }

    /**
     * Get task statistics
     */
    fun getTaskStatistics(): TaskStatistics {
        val allTasks = taskRegistry.values
        return TaskStatistics(
            totalTasks = allTasks.size,
            pendingTasks = allTasks.count { it.status == TaskStatus.PENDING.name },
            runningTasks = allTasks.count { it.status == TaskStatus.RUNNING.name },
            completedTasks = allTasks.count { it.status == TaskStatus.COMPLETED.name },
            failedTasks = allTasks.count { it.status == TaskStatus.FAILED.name },
            cancelledTasks = allTasks.count { it.status == TaskStatus.CANCELLED.name },
            scheduledTasks = allTasks.count { it.status == TaskStatus.SCHEDULED.name },
            averageExecutionTimeMs = allTasks
                .filter { it.executionTime > 0 }
                .takeIf { it.isNotEmpty() }
                ?.map { it.executionTime }
                ?.average()
                ?.toLong() ?: 0
        )
    }

    /**
     * Register a listener for task status changes
     */
    fun addTaskStatusListener(listener: TaskStatusListener) {
        taskListeners.add(listener)
    }

    /**
     * Remove a task status listener
     */
    fun removeTaskStatusListener(listener: TaskStatusListener) {
        taskListeners.remove(listener)
    }

    /**
     * Update task status and notify listeners
     */
    private fun updateTaskStatus(taskId: String, status: TaskStatus) {
        val task = taskRegistry[taskId] ?: return
        taskListeners.forEach { listener ->
            listener.onTaskStatusChanged(taskId, status, task)
        }
    }

    /**
     * Shutdown the scheduler
     */
    fun shutdown() {
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
        }
        Timber.d("Task scheduler shut down")
    }

    /**
     * Cleanup old tasks
     */
    fun cleanupOldTasks(ageMillis: Long = 7 * 24 * 60 * 60 * 1000) { // 7 days default
        val currentTime = System.currentTimeMillis()
        val tasksToDelete = taskRegistry.values
            .filter { (currentTime - it.completedAt!!) > ageMillis && it.completedAt != null }
            .map { it.taskId }

        tasksToDelete.forEach { taskId ->
            deleteTask(taskId)
        }

        Timber.d("Cleaned up ${tasksToDelete.size} old tasks")
    }
}

/**
 * Interface for listening to task status changes
 */
interface TaskStatusListener {
    fun onTaskStatusChanged(taskId: String, status: TaskStatus, task: PythonTask)
}

/**
 * Statistics about tasks
 */
data class TaskStatistics(
    val totalTasks: Int,
    val pendingTasks: Int,
    val runningTasks: Int,
    val completedTasks: Int,
    val failedTasks: Int,
    val cancelledTasks: Int,
    val scheduledTasks: Int,
    val averageExecutionTimeMs: Long
)
