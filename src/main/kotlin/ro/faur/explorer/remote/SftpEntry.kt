package ro.faur.explorer.remote

data class SftpEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val isSymlink: Boolean = false,
    val isBrokenSymlink: Boolean = false,
    val permissions: String? = null,
    val lastModified: Long = 0L,
    val path: String = name,
)
