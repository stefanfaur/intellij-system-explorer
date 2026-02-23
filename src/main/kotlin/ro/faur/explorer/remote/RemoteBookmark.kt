package ro.faur.explorer.remote

/**
 * A bookmark to a remote path on a specific connection.
 */
data class RemoteBookmark(
    var path: String = "",
    var label: String = "",
)
