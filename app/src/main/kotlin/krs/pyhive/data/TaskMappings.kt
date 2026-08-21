package krs.pyhive.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import krs.pyhive.models.PythonTask

/**
 * Extension functions to convert between [PythonTask] and [TaskEntity].
 */
private val gson = Gson()

fun PythonTask.toEntity(): TaskEntity = TaskEntity(
    taskId = taskId,
    scriptContent = scriptContent,
    scriptName = scriptName,
    status = status,
    scheduledTime = scheduledTime ?: 0,
    executionTime = executionTime,
    createdAt = createdAt,
    startedAt = startedAt ?: 0,
    completedAt = completedAt ?: 0,
    result = result,
    error = error,
    output = output,
    userId = userId,
    sandboxDir = sandboxDir,
    timeoutSeconds = timeoutSeconds,
    retryCount = retryCount,
    maxRetries = maxRetries,
    tagsJson = gson.toJson(tags),
    argsJson = argsJson,
    outputDir = outputDir,
    memoryUsage = memoryUsage
)

fun TaskEntity.copyFrom(task: PythonTask) {
    taskId = task.taskId
    scriptContent = task.scriptContent
    scriptName = task.scriptName
    status = task.status
    scheduledTime = task.scheduledTime ?: 0
    executionTime = task.executionTime
    createdAt = task.createdAt
    startedAt = task.startedAt ?: 0
    completedAt = task.completedAt ?: 0
    result = task.result
    error = task.error
    output = task.output
    userId = task.userId
    sandboxDir = task.sandboxDir
    timeoutSeconds = task.timeoutSeconds
    retryCount = task.retryCount
    maxRetries = task.maxRetries
    tagsJson = gson.toJson(task.tags)
    argsJson = task.argsJson
    outputDir = task.outputDir
    memoryUsage = task.memoryUsage
}

fun TaskEntity.toPythonTask(): PythonTask = PythonTask(
    taskId = taskId,
    scriptContent = scriptContent,
    scriptName = scriptName,
    status = status,
    scheduledTime = if (scheduledTime > 0) scheduledTime else null,
    executionTime = executionTime,
    createdAt = createdAt,
    startedAt = if (startedAt > 0) startedAt else null,
    completedAt = if (completedAt > 0) completedAt else null,
    result = result,
    error = error,
    output = output,
    userId = userId,
    sandboxDir = sandboxDir,
    timeoutSeconds = timeoutSeconds,
    retryCount = retryCount,
    maxRetries = maxRetries,
    tags = try {
        gson.fromJson(tagsJson, object : TypeToken<List<String>>() {}.type)
    } catch (_: Exception) {
        emptyList()
    },
    argsJson = argsJson,
    outputDir = outputDir,
    memoryUsage = memoryUsage
)