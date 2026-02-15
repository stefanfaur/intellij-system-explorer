package com.github.stefanfaur.explorer.util

object FileSizeFormatter {

    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    fun format(bytes: Long): String {
        if (bytes <= 0) return "0 B"

        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < UNITS.size - 1) {
            size /= 1024.0
            unitIndex++
        }

        return if (unitIndex == 0) {
            "${bytes} B"
        } else {
            "${"%.1f".format(size)} ${UNITS[unitIndex]}"
        }
    }
}
