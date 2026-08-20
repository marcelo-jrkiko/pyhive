package krs.pyhive.api

import krs.pyhive.api.ApiRequestContext
import krs.pyhive.python.PythonRuntimeManager
import krs.pyhive.api.ApiRoutes
import krs.pyhive.api.ApiController

class RuntimeApiController(
    private val pythonRuntimeManager: PythonRuntimeManager
) : ApiController {
    
    override fun supports(route: ApiRoutes.Match): Boolean {
        return when (route) {
            is ApiRoutes.Match.GetInstalledPackages -> true
            else -> false
        }
    }

    override  suspend fun handle(route: ApiRoutes.Match, context: ApiRequestContext) { 
        when (route) {
            is ApiRoutes.Match.GetInstalledPackages -> handleGetInstalledPackages(context)
            else -> context.sendError(404, "Endpoint not found")
        }
    }

    private fun handleGetInstalledPackages(requestContext: ApiRequestContext) {
        try {
            val packagesJson = pythonRuntimeManager.getInstalledPackages()
            requestContext.sendJson(200, packagesJson)
        } catch (e: Exception) {
            requestContext.sendError(500, "Failed to retrieve installed packages: ${e.message}")
        }
    }
}