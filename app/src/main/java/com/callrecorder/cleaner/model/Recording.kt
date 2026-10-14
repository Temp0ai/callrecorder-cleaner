package com.callrecorder.cleaner.model

import java.io.File
import java.util.Calendar

data class Recording(
    val file: File,
    val date: Calendar,
    var isMatched: Boolean = false
) {
    val displayName: String
        get() = file.nameWithoutExtension
    
    val fileSize: String
        get() {
            val size = file.length()
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> "${size / (1024 * 1024)} MB"
            }
        }
}
