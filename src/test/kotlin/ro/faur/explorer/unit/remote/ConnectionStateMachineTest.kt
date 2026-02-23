package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.ConnectionState
import ro.faur.explorer.remote.ConnectionStatus

class ConnectionStateMachineTest {

    @Test
    fun `initial state is disconnected`() {
        val state = ConnectionState()
        assertEquals(ConnectionStatus.DISCONNECTED, state.status)
    }

    @Test
    fun `connect transitions from disconnected to connecting`() {
        val state = ConnectionState()
        state.onConnecting()
        assertEquals(ConnectionStatus.CONNECTING, state.status)
    }

    @Test
    fun `successful auth transitions to connected`() {
        val state = ConnectionState()
        state.onConnecting()
        state.onConnected()
        assertEquals(ConnectionStatus.CONNECTED, state.status)
    }

    @Test
    fun `high latency transitions to degraded`() {
        val state = ConnectionState()
        state.onConnecting()
        state.onConnected()
        state.onLatencyUpdate(250)
        assertEquals(ConnectionStatus.DEGRADED, state.status)
    }

    @Test
    fun `low latency from degraded transitions back to connected`() {
        val state = ConnectionState()
        state.onConnecting()
        state.onConnected()
        state.onLatencyUpdate(250)
        state.onLatencyUpdate(50)
        assertEquals(ConnectionStatus.CONNECTED, state.status)
    }

    @Test
    fun `very high latency transitions to disconnected`() {
        val state = ConnectionState()
        state.onConnecting()
        state.onConnected()
        state.onLatencyUpdate(600)
        assertEquals(ConnectionStatus.DISCONNECTED, state.status)
    }

    @Test
    fun `connection error transitions to disconnected`() {
        val state = ConnectionState()
        state.onConnecting()
        state.onConnected()
        state.onConnectionLost()
        assertEquals(ConnectionStatus.DISCONNECTED, state.status)
    }

    @Test
    fun `connect from connecting is no-op`() {
        val state = ConnectionState()
        state.onConnecting()
        state.onConnecting()
        assertEquals(ConnectionStatus.CONNECTING, state.status)
    }

    @Test
    fun `disconnect from disconnected is no-op`() {
        val state = ConnectionState()
        state.onDisconnected()
        assertEquals(ConnectionStatus.DISCONNECTED, state.status)
    }

    @Test
    fun `latencyMs returns null when disconnected`() {
        val state = ConnectionState()
        assertNull(state.latencyMs)
    }

    @Test
    fun `latencyMs returns last measured value when connected`() {
        val state = ConnectionState()
        state.onConnecting()
        state.onConnected()
        state.onLatencyUpdate(42)
        assertEquals(42, state.latencyMs)
    }

    @Test
    fun `status indicator for connected with low latency is GREEN`() {
        val state = ConnectionState()
        state.onConnecting()
        state.onConnected()
        state.onLatencyUpdate(50)
        assertEquals(ConnectionState.Indicator.GREEN, state.indicator)
    }

    @Test
    fun `status indicator for degraded is YELLOW`() {
        val state = ConnectionState()
        state.onConnecting()
        state.onConnected()
        state.onLatencyUpdate(200)
        assertEquals(ConnectionState.Indicator.YELLOW, state.indicator)
    }

    @Test
    fun `status indicator for disconnected is RED`() {
        val state = ConnectionState()
        assertEquals(ConnectionState.Indicator.RED, state.indicator)
    }
}
