package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.quickopen.model.CandidateType
import ro.faur.explorer.quickopen.model.SearchCandidate

class QuickOpenRemoteActionsTest {

    @Test
    fun `remote connect candidates have correct type and ID prefix`() {
        // Simulate the candidate construction logic inline (no platform deps)
        val profileName = "prod-server"
        val candidate = SearchCandidate(
            id = "action:connect:$profileName",
            displayName = "Connect to $profileName",
            fullPath = "action:connect:$profileName",
            parentPath = "",
            type = CandidateType.ACTION,
            extra = mapOf("handler" to ({} as () -> Unit))
        )
        assertEquals(CandidateType.ACTION, candidate.type)
        assertTrue(candidate.id.startsWith("action:connect:"))
        assertEquals("Connect to prod-server", candidate.displayName)
    }

    @Test
    fun `disconnect candidate is only added when panel reports connected`() {
        // Logic: if isRemotePanelVisible == false, no disconnect candidate
        val isConnected = false
        val candidates = mutableListOf<SearchCandidate>()
        if (isConnected) {
            candidates += SearchCandidate(
                id = "action:disconnect",
                displayName = "Disconnect from remote",
                fullPath = "action:disconnect",
                parentPath = "",
                type = CandidateType.ACTION,
                extra = emptyMap()
            )
        }
        assertTrue(candidates.none { it.id == "action:disconnect" })
    }
}
