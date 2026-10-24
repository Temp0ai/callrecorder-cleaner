package com.callrecorder.cleaner.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.*

data class Recording(
    val uri: Uri,
    val displayName: String,
    val fileSize: Long,
    val date: Calendar,
    var isMatched: Boolean = false
) {
    val fileSizeFormatted: String
        get() = when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            else -> "${fileSize / (1024 * 1024)} MB"
        }

    val dateFormatted: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            return sdf.format(date.time)
        }
}
