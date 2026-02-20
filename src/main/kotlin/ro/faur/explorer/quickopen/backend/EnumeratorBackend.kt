package ro.faur.explorer.quickopen.backend

import kotlinx.coroutines.flow.Flow

interface EnumeratorBackend {
    fun isAvailable(): Boolean
    fun enumerate(root: String, maxResults: Int): Flow<String>
    val name: String
}
