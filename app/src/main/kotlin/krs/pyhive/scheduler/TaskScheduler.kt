package krs.pyhive.scheduler

import android.content.Context
import krs.pyhive.data.TaskRepository
import krs.pyhive.models.PythonTask
import krs.pyhive.models.TaskStatus
import krs.pyhive.models.TaskStatistics
import krs.pyhive.python.PythonRuntimeManager
import krs.pyhive.sandbox.SandboxManager
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Manages task scheduling, execution, and lifecycle.
 * Persists tasks via [TaskRepository] backed by ObjectBox.
 */
class TaskScheduler(
    private val context: Context,
    private val pythonRuntimeManager: PythonRuntimeManager,
    private val sandboxManager: SandboxManager,
    private val taskRepository: TaskRepository
) {

    companion object {
        private const val MAX_CONCURRENT_TASKS = 4
    }

    private val executorService: ScheduledExecutorService =
        Executors.newScheduledThreadPool(MAX_CONCURRENT_TASKS)

    private val taskListeners = mutableListOf<TaskStatusListener>()

    /**
     * Submit a task for immediate execution or scheduling
     */
    fun submitTask(task: PythonTask): String {
        taskRepository.save(task)

        return if (task.scheduledTime != null && task.scheduledTime!! > System.currentTimeMillis()) {
            scheduleTask(task)
            task.taskId
        } else {
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
                val current = taskRepository.get(task.taskId) ?: return@execute
                val updatedTask = current.copy(
                    status = if (result.success) TaskStatus.COMPLETED.name else TaskStatus.FAILED.name,
                    result = result.result,
                    error = result.error,
                    output = result.output,
                    executionTime = result.executionTimeMs,
                    completedAt = System.currentTimeMillis(),
                    startedAt = current.startedAt ?: System.currentTimeMillis()
                )
                taskRepository.save(updatedTask)

                val newStatus = if (result.success) TaskStatus.COMPLETED else TaskStatus.FAILED
                updateTaskStatus(task.taskId, newStatus)

                if (result.error.isNotEmpty()) {
                    Timber.e("Task ${task.taskId} failed with error: ${result.error}")
                } else {
                    Timber.d("Task ${task.taskId} completed successfully")
                }

                Timber.d("Task ${task.taskId} completed: ${result.output}")
            } catch (e: Exception) {
                Timber.e(e, "Error executing task ${task.taskId}")
                val current = taskRepository.get(task.taskId) ?: return@execute
                val failedTask = current.copy(
                    status = TaskStatus.FAILED.name,
                    error = e.message ?: "Unknown error",
                    completedAt = System.currentTimeMillis(),
                    startedAt = current.startedAt ?: System.currentTimeMillis()
                )
                taskRepository.save(failedTask)
                updateTaskStatus(task.taskId, TaskStatus.FAILED)
            } finally {
                // Always remove sandbox files once task execution finishes.
                sandboxManager.deleteSandbox(task.taskId)
            }
        }
    }

    /**
     * Schedule a task for future execution
     */
    private fun scheduleTask(task: PythonTask) {
        val delayMs = (task.scheduledTime!! - System.currentTimeMillis()).coerceAtLeast(0)

        executorService.schedule({
            val currentTask = taskRepository.get(task.taskId) ?: return@schedule
            executeTask(currentTask)
        }, delayMs, TimeUnit.MILLISECONDS)

        updateTaskStatus(task.taskId, TaskStatus.SCHEDULED)
        Timber.d("Task ${task.taskId} scheduled for ${delayMs}ms from now")
    }

    /**
     * Get task by ID
     */
    fun getTask(taskId: String): PythonTask? = taskRepository.get(taskId)

    /**
     * Get all tasks
     */
    fun getAllTasks(): List<PythonTask> = taskRepository.getAll()

    /**
     * Get tasks by status
     */
    fun getTasksByStatus(status: TaskStatus): List<PythonTask> =
        taskRepository.getByStatus(status)

    /**
     * Get tasks by tag
     */
    fun getTasksByTag(tag: String): List<PythonTask> =
        taskRepository.getByTag(tag)

    /**
     * Cancel a task
     */
    fun cancelTask(taskId: String): Boolean {
        val task = taskRepository.get(taskId) ?: return false

        return when (TaskStatus.valueOf(task.status)) {
            TaskStatus.PENDING, TaskStatus.SCHEDULED -> {
                val cancelledTask = task.copy(
                    status = TaskStatus.CANCELLED.name,
                    completedAt = System.currentTimeMillis()
                )
                taskRepository.save(cancelledTask)
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
        val task = taskRepository.get(taskId) ?: return false

        return when (TaskStatus.valueOf(task.status)) {
            TaskStatus.PENDING, TaskStatus.SCHEDULED -> {
                val rescheduledTask = task.copy(scheduledTime = newScheduledTime)
                taskRepository.save(rescheduledTask)
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
        val task = taskRepository.get(taskId) ?: return false

        return if (task.retryCount < task.maxRetries && task.status == TaskStatus.FAILED.name) {
            val retriedTask = task.copy(
                retryCount = task.retryCount + 1,
                status = TaskStatus.PENDING.name,
                error = "",
                output = ""
            )
            taskRepository.save(retriedTask)
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
        sandboxManager.deleteSandbox(taskId)
        val deleted = taskRepository.delete(taskId)
        if (deleted) Timber.d("Task $taskId deleted")
        return deleted
    }

    /**
     * Get task statistics
     */
    fun getTaskStatistics(): TaskStatistics = taskRepository.getStatistics()

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
        val task = taskRepository.get(taskId) ?: return
        taskRepository.updateStatus(taskId, status)
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
        taskRepository.cleanupOldTasks(ageMillis)
    }
}

/**
 * Interface for listening to task status changes
 */
interface TaskStatusListener {
    fun onTaskStatusChanged(taskId: String, status: TaskStatus, task: PythonTask)
}
