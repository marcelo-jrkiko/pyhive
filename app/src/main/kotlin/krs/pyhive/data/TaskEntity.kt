package krs.pyhive.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

/**
 * Simple ObjectBox entity for persisting PythonTask data.
 * Uses minimal annotations — just @Entity and @Id.
 */
@Entity
data class TaskEntity(
    @Id var id: Long = 0,
    var taskId: String = "",
    var scriptContent: String = "",
    var scriptName: String = "",
    var status: String = "",
    var scheduledTime: Long = 0,
    var executionTime: Long = 0,
    var createdAt: Long = 0,
    var startedAt: Long = 0,
    var completedAt: Long = 0,
    var result: String = "",
    var error: String = "",
    var output: String = "",
    var userId: String = "",
    var sandboxDir: String = "",
    var timeoutSeconds: Long = 300,
    var retryCount: Int = 0,
    var maxRetries: Int = 3,
    var tagsJson: String = "[]",
    var argsJson: String = "{}"
)