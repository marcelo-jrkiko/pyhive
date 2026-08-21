package krs.pyhive.models

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.util.*

/**
 * Represents the status of a task
 */
enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    SCHEDULED
}

/**
 * Data class representing a Python Task
 */
@Parcelize
data class PythonTask(
    val taskId: String = UUID.randomUUID().toString(),
    
    @SerializedName("script_content")
    val scriptContent: String,
    
    @SerializedName("script_name")
    val scriptName: String = "task_${System.currentTimeMillis()}.py",
    
    @SerializedName("status")
    var status: String = TaskStatus.PENDING.name,
    
    @SerializedName("scheduled_time")
    var scheduledTime: Long? = null,
    
    @SerializedName("execution_time")
    var executionTime: Long = 0,
    
    @SerializedName("created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @SerializedName("started_at")
    var startedAt: Long? = null,
    
    @SerializedName("completed_at")
    var completedAt: Long? = null,
    
    @SerializedName("result")
    var result: String = "",
    
    @SerializedName("error")
    var error: String = "",
    
    @SerializedName("output")
    var output: String = "",
    
    @SerializedName("user_id")
    val userId: String = "default_user",
    
    @SerializedName("sandbox_dir")
    val sandboxDir: String = "",
    
    @SerializedName("timeout_seconds")
    val timeoutSeconds: Long = 300,
    
    @SerializedName("retry_count")
    var retryCount: Int = 0,
    
    @SerializedName("max_retries")
    val maxRetries: Int = 3,
    
    @SerializedName("tags")
    val tags: List<String> = emptyList(),

    @SerializedName("args_json")
    val argsJson: String = "{}"
) : Parcelable

/**
 * Request model for submitting a new task
 */
@Parcelize
data class SubmitTaskRequest(
    @SerializedName("script_content")
    val scriptContent: String,
    
    @SerializedName("script_name")
    val scriptName: String? = null,
    
    @SerializedName("scheduled_time")
    val scheduledTime: Long? = null,
    
    @SerializedName("timeout_seconds")
    val timeoutSeconds: Long = 300,
    
    @SerializedName("tags")
    val tags: List<String> = emptyList()
) : Parcelable

/**
 * Parameters for submitting a task via multipart form.
 * The script content and optional args JSON are provided separately
 * as multipart `script` and `args` fields.
 */
data class SubmitTaskParams(
    @SerializedName("script_name")
    val scriptName: String? = null,
    
    @SerializedName("scheduled_time")
    val scheduledTime: Long? = null,
    
    @SerializedName("timeout_seconds")
    val timeoutSeconds: Long = 300,
    
    @SerializedName("tags")
    val tags: List<String> = emptyList()
)

/**
 * Response model for task submission
 */
@Parcelize
data class TaskResponse(
    @SerializedName("task_id")
    val taskId: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("message")
    val message: String = "",
    
    @SerializedName("created_at")
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable

/**
 * Response model for task status
 */
@Parcelize
data class TaskStatusResponse(
    @SerializedName("task_id")
    val taskId: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("execution_time")
    val executionTime: Long = 0,
    
    @SerializedName("result")
    val result: String? = null,
    
    @SerializedName("output")
    val output: String? = null,
    
    @SerializedName("error")
    val error: String? = null,
    
    @SerializedName("progress")
    val progress: Int = 0
) : Parcelable

/**
 * Response model for multiple tasks
 */
@Parcelize
data class TaskListResponse(
    @SerializedName("tasks")
    val tasks: List<TaskStatusResponse>,
    
    @SerializedName("total_count")
    val totalCount: Int,
    
    @SerializedName("filtered_count")
    val filteredCount: Int
) : Parcelable

/**
 * Error response model
 */
@Parcelize
data class ErrorResponse(
    @SerializedName("error_code")
    val errorCode: String,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("details")
    val details: String? = null,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

/**
 * Reschedule task request
 */
@Parcelize
data class RescheduleTaskRequest(
    @SerializedName("new_scheduled_time")
    val newScheduledTime: Long
) : Parcelable

/**
 * Task execution result
 */
@Parcelize
data class TaskExecutionResult(
    val taskId: String,
    val success: Boolean,
    val result: String = "",
    val output: String = "",
    val error: String = "",
    val executionTime: Long = 0
) : Parcelable


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

/**
 * Response model for task log tail endpoint.
 */
data class TaskLogsResponse(
    @SerializedName("task_id")
    val taskId: String,

    @SerializedName("lines")
    val lines: Array<String>,

    @SerializedName("lines_requested")
    val linesRequested: Int,

    @SerializedName("total_lines")
    val totalLines: Int
)
