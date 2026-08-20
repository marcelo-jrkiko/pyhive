package krs.pyhive.api

/**
 * Centralized API route definitions and matching helpers.
 */
object ApiRoutes {
    private const val API_PREFIX = "/api"

    private val tasksRoute = Regex("^$API_PREFIX/tasks$")
    private val taskByIdRoute = Regex("^$API_PREFIX/tasks/(\\w+)$")
    private val cancelTaskRoute = Regex("^$API_PREFIX/tasks/(\\w+)/cancel$")
    private val rescheduleTaskRoute = Regex("^$API_PREFIX/tasks/(\\w+)/reschedule$")
    private val statsRoute = Regex("^$API_PREFIX/stats$")
    private val healthRoute = Regex("^$API_PREFIX/health$")

    sealed class Match {
        object SubmitTask : Match()
        data class GetTaskStatus(val taskId: String) : Match()
        data class ListTasks(val query: String) : Match()
        data class CancelTask(val taskId: String) : Match()
        data class RescheduleTask(val taskId: String) : Match()
        data class DeleteTask(val taskId: String) : Match()
        object GetStats : Match()
        object HealthCheck : Match()
    }

    fun match(method: String, rawPath: String): Match? {
        val path = rawPath.substringBefore("?")
        val query = rawPath.substringAfter("?", "")

        return when (method) {
            "POST" -> {
                if (tasksRoute.matches(path)) Match.SubmitTask else null
            }

            "GET" -> {
                when {
                    tasksRoute.matches(path) -> Match.ListTasks(query)
                    taskByIdRoute.matchEntire(path) != null -> {
                        val taskId = taskByIdRoute.matchEntire(path)!!.groupValues[1]
                        Match.GetTaskStatus(taskId)
                    }

                    statsRoute.matches(path) -> Match.GetStats
                    healthRoute.matches(path) -> Match.HealthCheck
                    else -> null
                }
            }

            "PUT" -> {
                when {
                    cancelTaskRoute.matchEntire(path) != null -> {
                        val taskId = cancelTaskRoute.matchEntire(path)!!.groupValues[1]
                        Match.CancelTask(taskId)
                    }

                    rescheduleTaskRoute.matchEntire(path) != null -> {
                        val taskId = rescheduleTaskRoute.matchEntire(path)!!.groupValues[1]
                        Match.RescheduleTask(taskId)
                    }

                    else -> null
                }
            }

            "DELETE" -> {
                if (taskByIdRoute.matchEntire(path) != null) {
                    val taskId = taskByIdRoute.matchEntire(path)!!.groupValues[1]
                    Match.DeleteTask(taskId)
                } else {
                    null
                }
            }

            else -> null
        }
    }
}