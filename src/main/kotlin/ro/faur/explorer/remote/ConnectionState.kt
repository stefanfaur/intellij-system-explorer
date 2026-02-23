package ro.faur.explorer.remote

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DEGRADED
}

class ConnectionState {

    enum class Indicator {
        GREEN,
        YELLOW,
        RED
    }

    var status: ConnectionStatus = ConnectionStatus.DISCONNECTED
        private set

    var latencyMs: Int? = null
        private set

    val indicator: Indicator
        get() = when (status) {
            ConnectionStatus.CONNECTED -> if ((latencyMs ?: Int.MAX_VALUE) < 100) Indicator.GREEN else Indicator.YELLOW
            ConnectionStatus.DEGRADED -> Indicator.YELLOW
            ConnectionStatus.DISCONNECTED, ConnectionStatus.CONNECTING -> Indicator.RED
        }

    fun onConnecting() {
        if (status == ConnectionStatus.DISCONNECTED) {
            status = ConnectionStatus.CONNECTING
        }
    }

    fun onConnected() {
        if (status == ConnectionStatus.CONNECTING) {
            status = ConnectionStatus.CONNECTED
        }
    }

    fun onDisconnected() {
        status = ConnectionStatus.DISCONNECTED
        latencyMs = null
    }

    fun onConnectionLost() {
        onDisconnected()
    }

    fun onLatencyUpdate(ms: Int) {
        if (status != ConnectionStatus.CONNECTED && status != ConnectionStatus.DEGRADED) return
        when {
            ms < 100 -> {
                latencyMs = ms
                status = ConnectionStatus.CONNECTED
            }
            ms <= 500 -> {
                latencyMs = ms
                status = ConnectionStatus.DEGRADED
            }
            else -> {
                onDisconnected()
            }
        }
    }
}
