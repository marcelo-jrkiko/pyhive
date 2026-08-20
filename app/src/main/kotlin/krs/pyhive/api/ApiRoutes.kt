package krs.pyhive.api

/**
 * Centralized API route definitions and matching helpers.
 */
object ApiRoutes {
    private const val API_PREFIX = "/api"

    // Task ID segment: word chars plus hyphens to support UUIDs (e.g. 4b6dab16-2be5-4ec7-9c94-5ddbb23f3827)
    private const val TASK_ID_SEG = "[\\w-]+"

    sealed class Match {
        object SubmitTask : Match()
        data class GetTaskStatus(val taskId: String) : Match()
        data class ListTasks(val query: String) : Match()
        data class CancelTask(val taskId: String) : Match()
        data class RescheduleTask(val taskId: String) : Match()
        data class DeleteTask(val taskId: String) : Match()
        object GetStats : Match()
        object HealthCheck : Match()
        /** Preflight OPTIONS request for any API path. */
        data class Options(val path: String) : Match()
    }

    /**
     * Describes a single route: the HTTP method it handles, the regex it matches against the
     * path, and a factory that turns a [MatchResult] plus the raw query string into a [Match].
     */
    data class RouteDefinition(
        val method: String,
        val pathRegex: Regex,
        val handler: (result: MatchResult, query: String) -> Match
    )

    /** All application routes, evaluated in order. OPTIONS is handled separately (see [match]). */
    val routes: List<RouteDefinition> = listOf(
        RouteDefinition("POST",   Regex("^$API_PREFIX/tasks$"))                              { _, _     -> Match.SubmitTask },
        RouteDefinition("GET",    Regex("^$API_PREFIX/tasks$"))                              { _, query -> Match.ListTasks(query) },
        RouteDefinition("GET",    Regex("^$API_PREFIX/tasks/($TASK_ID_SEG)$"))              { r, _     -> Match.GetTaskStatus(r.groupValues[1]) },
        RouteDefinition("GET",    Regex("^$API_PREFIX/stats$"))                              { _, _     -> Match.GetStats },
        RouteDefinition("GET",    Regex("^$API_PREFIX/health$"))                             { _, _     -> Match.HealthCheck },
        RouteDefinition("PUT",    Regex("^$API_PREFIX/tasks/($TASK_ID_SEG)/cancel$"))       { r, _     -> Match.CancelTask(r.groupValues[1]) },
        RouteDefinition("PUT",    Regex("^$API_PREFIX/tasks/($TASK_ID_SEG)/reschedule$"))   { r, _     -> Match.RescheduleTask(r.groupValues[1]) },
        RouteDefinition("DELETE", Regex("^$API_PREFIX/tasks/($TASK_ID_SEG)$"))              { r, _     -> Match.DeleteTask(r.groupValues[1]) },
    )

    fun match(method: String, rawPath: String): Match? {
        if (method == "OPTIONS") return Match.Options(rawPath.substringBefore("?"))

        val path  = rawPath.substringBefore("?")
        val query = rawPath.substringAfter("?", "")

        for (route in routes) {
            if (route.method != method) continue
            val result = route.pathRegex.matchEntire(path) ?: continue
            return route.handler(result, query)
        }
        return null
    }
}