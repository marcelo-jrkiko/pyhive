package krs.pyhive.utils

import java.io.File
import android.content.Context

/**
 * Utility functions for file operations
 */
object FileUtils {

    /**
     * Copy file from source to destination
     */
    fun copyFile(source: File, destination: File) {
        source.copyTo(destination, overwrite = true)
    }

    /**
     * Read file content
     */
    fun readFile(file: File): String {
        return file.readText()
    }

    /**
     * Write content to file
     */
    fun writeFile(file: File, content: String) {
        file.writeText(content)
    }

    /**
     * Delete file or directory recursively
     */
    fun deleteFile(file: File): Boolean {
        return if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    /**
     * Get directory size
     */
    fun getDirectorySize(file: File): Long {
        var size = 0L
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                size += getDirectorySize(child)
            }
        } else {
            size = file.length()
        }
        return size
    }
}

/**
 * Utility functions for string operations
 */
object StringUtils {

    /**
     * Format bytes to human readable format
     */
    fun formatBytes(bytes: Long): String {
        when {
            bytes <= 0 -> return "0 B"
            bytes < 1024 -> return "$bytes B"
            bytes < 1024 * 1024 -> return String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> return String.format("%.2f MB", bytes / (1024.0 * 1024))
            else -> return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * Format time duration
     */
    fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
            minutes > 0 -> String.format("%02d:%02d", minutes, seconds % 60)
            else -> String.format("%d s", seconds)
        }
    }


    fun escapePythonString(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }
}

/**
 * Utility functions for JSON operations
 */
object JsonUtils {

    /**
     * Pretty print JSON
     */
    fun prettyPrintJson(json: String): String {
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val jsonObject = com.google.gson.JsonParser.parseString(json)
        return gson.toJson(jsonObject)
    }
}


object AssetUtils {
    fun readAssetText(ctx: Context, assetPath: String): String {
        return ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
    }
}