package krs.pyhive.api

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import krs.pyhive.models.PythonTask
import krs.pyhive.models.RescheduleTaskRequest
import krs.pyhive.models.SubmitTaskParams
import krs.pyhive.models.TaskListResponse
import krs.pyhive.models.TaskLogsResponse
import krs.pyhive.models.TaskResponse
import krs.pyhive.models.TaskStatus
import krs.pyhive.models.TaskStatusResponse
import krs.pyhive.preferences.AppPreferences
import krs.pyhive.scheduler.TaskScheduler
import timber.log.Timber
import java.io.File

/**
 * Dedicated controller for all task lifecycle endpoints.
 */
class TaskApiController(
    private val context: Context,
    private val taskScheduler: TaskScheduler
) : ApiController {

    override fun supports(route: ApiRoutes.Match): Boolean {
        return when (route) {
            is ApiRoutes.Match.SubmitTask,
            is ApiRoutes.Match.GetTaskStatus,
            is ApiRoutes.Match.ListTasks,
            is ApiRoutes.Match.CancelTask,
            is ApiRoutes.Match.RescheduleTask,
            is ApiRoutes.Match.DeleteTask,
            is ApiRoutes.Match.GetTaskLogs -> true

            else -> false
        }
    }

    override suspend fun handle(route: ApiRoutes.Match, context: ApiRequestContext) {
        when (route) {
            is ApiRoutes.Match.SubmitTask -> handleSubmitTask(context)
            is ApiRoutes.Match.GetTaskStatus -> handleGetTaskStatus(context, route.taskId)
            is ApiRoutes.Match.ListTasks -> handleListTasks(context, route.query)
            is ApiRoutes.Match.CancelTask -> handleCancelTask(context, route.taskId)
            is ApiRoutes.Match.RescheduleTask -> handleRescheduleTask(context, route.taskId)
            is ApiRoutes.Match.DeleteTask -> handleDeleteTask(context, route.taskId)
            is ApiRoutes.Match.GetTaskLogs -> handleGetTaskLogs(context, route.taskId, route.query)
            else -> context.sendError(404, "Endpoint not found")
        }
    }

    private fun handleSubmitTask(requestContext: ApiRequestContext) {
        try {
            val fields = requestContext.readMultipartForm()
            val paramsJson = fields["params"]
                ?: throw IllegalArgumentException("Missing 'params' field")
            val scriptContent = fields["script"]
                ?: throw IllegalArgumentException("Missing 'script' field")
            val argsJson = fields["args"]

            val params = requestContext.parseJson<SubmitTaskParams>(paramsJson)
            val appPreferences = AppPreferences(context)
            val timeoutSeconds =
                if (params.timeoutSeconds > 0) params.timeoutSeconds
                else appPreferences.defaultTaskTimeoutSeconds()

            val task = PythonTask(
                scriptContent = scriptContent,
                scriptName = params.scriptName ?: "task_${System.currentTimeMillis()}.py",
                scheduledTime = params.scheduledTime,
                timeoutSeconds = timeoutSeconds,
                tags = params.tags,
                argsJson = normalizeArgsJson(argsJson)
            )

            val taskId = taskScheduler.submitTask(task)
            val response = TaskResponse(
                taskId = taskId,
                status = TaskStatus.PENDING.name,
                message = "Task submitted successfully"
            )

            requestContext.sendJson(202, response)
            Timber.d("Task submitted: $taskId")
        } catch (e: Exception) {
            Timber.e("Error submitting task: $e")
            requestContext.sendError(400, "Failed to submit task: ${e.message}")
        }
    }

    private fun handleGetTaskStatus(requestContext: ApiRequestContext, taskId: String) {
        try {
            val task = taskScheduler.getTask(taskId)

            if (task == null) {
                requestContext.sendError(404, "Task not found: $taskId")
                return
            }

            val response = TaskStatusResponse(
                taskId = task.taskId,
                status = task.status,
                executionTime = task.executionTime,
                result = if (task.result.isNotEmpty()) task.result else null,
                output = if (task.output.isNotEmpty()) task.output else null,
                error = if (task.error.isNotEmpty()) task.error else null
            )

            requestContext.sendJson(200, response)
        } catch (e: Exception) {
            Timber.e("Error getting task status: $e")
            requestContext.sendError(500, "Failed to get task status")
        }
    }

    private fun handleListTasks(requestContext: ApiRequestContext, query: String) {
        try {
            val params = requestContext.parseQueryParams(query)
            val status = params["status"]
            val tag = params["tag"]
            val limit = params["limit"]?.toIntOrNull() ?: 100

            val tasks = when {
                status != null -> taskScheduler.getTasksByStatus(TaskStatus.valueOf(status))
                tag != null -> taskScheduler.getTasksByTag(tag)
                else -> taskScheduler.getAllTasks()
            }.take(limit)

            val taskResponses = tasks.map { task ->
                TaskStatusResponse(
                    taskId = task.taskId,
                    status = task.status,
                    executionTime = task.executionTime,
                    result = if (task.result.isNotEmpty()) task.result else null,
                    output = if (task.output.isNotEmpty()) task.output else null,
                    error = if (task.error.isNotEmpty()) task.error else null
                )
            }

            val response = TaskListResponse(
                tasks = taskResponses,
                totalCount = taskScheduler.getAllTasks().size,
                filteredCount = tasks.size
            )

            requestContext.sendJson(200, response)
        } catch (e: Exception) {
            Timber.e("Error listing tasks: $e")
            requestContext.sendError(500, "Failed to list tasks")
        }
    }

    private fun handleCancelTask(requestContext: ApiRequestContext, taskId: String) {
        try {
            val success = taskScheduler.cancelTask(taskId)

            if (!success) {
                requestContext.sendError(409, "Cannot cancel task $taskId (not in valid state)")
                return
            }

            val response = mapOf(
                "message" to "Task cancelled successfully",
                "task_id" to taskId
            )

            requestContext.sendJson(200, response)
        } catch (e: Exception) {
            Timber.e("Error cancelling task: $e")
            requestContext.sendError(500, "Failed to cancel task")
        }
    }

    private fun handleRescheduleTask(requestContext: ApiRequestContext, taskId: String) {
        try {
            val payload = requestContext.readRequestPayload()
            val request = requestContext.parseJson<RescheduleTaskRequest>(payload)

            val success = taskScheduler.rescheduleTask(taskId, request.newScheduledTime)

            if (!success) {
                requestContext.sendError(409, "Cannot reschedule task $taskId (not in valid state)")
                return
            }

            val response = mapOf(
                "message" to "Task rescheduled successfully",
                "task_id" to taskId,
                "new_scheduled_time" to request.newScheduledTime
            )

            requestContext.sendJson(200, response)
        } catch (e: Exception) {
            Timber.e("Error rescheduling task: $e")
            requestContext.sendError(400, "Failed to reschedule task: ${e.message}")
        }
    }

    private fun handleDeleteTask(requestContext: ApiRequestContext, taskId: String) {
        try {
            val success = taskScheduler.deleteTask(taskId)

            if (!success) {
                requestContext.sendError(404, "Task not found: $taskId")
                return
            }

            val response = mapOf(
                "message" to "Task deleted successfully",
                "task_id" to taskId
            )

            requestContext.sendJson(200, response)
        } catch (e: Exception) {
            Timber.e("Error deleting task: $e")
            requestContext.sendError(500, "Failed to delete task")
        }
    }

    private fun handleGetTaskLogs(requestContext: ApiRequestContext, taskId: String, query: String) {
        try {
            val task = taskScheduler.getTask(taskId)
            if (task == null) {
                requestContext.sendError(404, "Task not found: $taskId")
                return
            }

            val params = requestContext.parseQueryParams(query)
            val lines = params["lines"]?.toIntOrNull() ?: 100

            val outputDir = File(context.getExternalFilesDir(null), "task_output/$taskId")
            val stdoutFile = File(outputDir, "stdout.log")
            val stderrFile = File(outputDir, "stderr.log")

            val (stdoutLines, stdoutTotal) = readLastLines(stdoutFile, lines)
            val (stderrLines, stderrTotal) = readLastLines(stderrFile, lines)

            val response = TaskLogsResponse(
                taskId = taskId,
                lines = arrayOf(*stdoutLines.toTypedArray(), *stderrLines.toTypedArray()),
                linesRequested = lines,
                totalLines = stdoutTotal + stderrTotal
            )

            requestContext.sendJson(200, response)
        } catch (e: Exception) {
            Timber.e("Error getting task logs: $e")
            requestContext.sendError(500, "Failed to get task logs: ${e.message}")
        }
    }

    /**
     * Reads the last [maxLines] lines from the given file.
     * Returns a pair of (requested lines, total line count).
     * If the file does not exist, returns (emptyList(), 0).
     */
    private fun readLastLines(file: File, maxLines: Int): Pair<List<String>, Int> {
        if (!file.exists() || !file.isFile) {
            return Pair(emptyList(), 0)
        }
        val allLines = file.readLines()
        val tail = if (allLines.size <= maxLines) allLines else allLines.takeLast(maxLines)
        return Pair(tail, allLines.size)
    }

    private fun normalizeArgsJson(argsPayload: String?): String {
        if (argsPayload.isNullOrBlank()) {
            return "{}"
        }

        val parsed: JsonElement = JsonParser.parseString(argsPayload)

        if (!parsed.isJsonObject) {
            throw IllegalArgumentException("'args' must be a JSON object")
        }

        return parsed.toString()
    }
}