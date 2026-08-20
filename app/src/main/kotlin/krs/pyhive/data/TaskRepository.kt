package krs.pyhive.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.objectbox.Box
import io.objectbox.BoxStore
import krs.pyhive.models.PythonTask
import krs.pyhive.models.TaskStatus
import krs.pyhive.models.TaskStatistics
import timber.log.Timber

/**
 * Repository for persisting and querying PythonTask data via ObjectBox.
 * Maps between [PythonTask] (domain model) and [TaskEntity] (ObjectBox entity).
 */
class TaskRepository(private val boxStore: BoxStore) {

    private val taskBox: Box<TaskEntity> = boxStore.boxFor(TaskEntity::class.java)
    private val gson = Gson()

    // ---------- Save / Update ----------

    fun save(task: PythonTask) {
        val existing = findByTaskId(task.taskId)
        if (existing != null) {
            existing.copyFrom(task)
            taskBox.put(existing)
        } else {
            taskBox.put(task.toEntity())
        }
    }

    fun updateStatus(taskId: String, status: TaskStatus) {
        val entity = findByTaskId(taskId) ?: return
        entity.status = status.name
        taskBox.put(entity)
    }

    // ---------- Read ----------

    fun get(taskId: String): PythonTask? =
        findByTaskId(taskId)?.toPythonTask()

    fun getAll(): List<PythonTask> =
        taskBox.all.map { it.toPythonTask() }

    fun getByStatus(status: TaskStatus): List<PythonTask> =
        taskBox.query(TaskEntity_.status.equal(status.name))
            .build()
            .find()
            .map { it.toPythonTask() }

    fun getByTag(tag: String): List<PythonTask> =
        taskBox.all.filter { entity ->
            val tags = parseTags(entity.tagsJson)
            tag in tags
        }.map { it.toPythonTask() }

    // ---------- Delete ----------

    fun delete(taskId: String): Boolean {
        val entity = findByTaskId(taskId) ?: return false
        taskBox.remove(entity)
        Timber.d("Task $taskId deleted from repository")
        return true
    }

    fun deleteAll() {
        taskBox.removeAll()
    }

    // ---------- Statistics ----------

    fun getStatistics(): TaskStatistics {
        val all = taskBox.all
        return TaskStatistics(
            totalTasks = all.size,
            pendingTasks = all.count { it.status == TaskStatus.PENDING.name },
            runningTasks = all.count { it.status == TaskStatus.RUNNING.name },
            completedTasks = all.count { it.status == TaskStatus.COMPLETED.name },
            failedTasks = all.count { it.status == TaskStatus.FAILED.name },
            cancelledTasks = all.count { it.status == TaskStatus.CANCELLED.name },
            scheduledTasks = all.count { it.status == TaskStatus.SCHEDULED.name },
            averageExecutionTimeMs = all
                .filter { it.executionTime > 0 }
                .takeIf { it.isNotEmpty() }
                ?.map { it.executionTime }
                ?.average()
                ?.toLong() ?: 0
        )
    }

    fun cleanupOldTasks(ageMillis: Long = 7 * 24 * 60 * 60 * 1000) {
        val currentTime = System.currentTimeMillis()
        val toDelete = taskBox.all.filter {
            it.completedAt > 0 && (currentTime - it.completedAt) > ageMillis
        }
        toDelete.forEach { taskBox.remove(it) }
        Timber.d("Cleaned up ${toDelete.size} old tasks from repository")
    }

    // ---------- Helpers ----------

    private fun findByTaskId(taskId: String): TaskEntity? =
        taskBox.query(TaskEntity_.taskId.equal(taskId))
            .build()
            .findFirst()

    private fun parseTags(json: String): List<String> =
        try {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
        } catch (_: Exception) {
            emptyList()
        }
}