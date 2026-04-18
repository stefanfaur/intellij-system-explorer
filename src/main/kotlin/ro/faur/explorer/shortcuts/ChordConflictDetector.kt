package ro.faur.explorer.shortcuts

/**
 * Detects and resolves conflicts for chord shortcuts.
 * 
 * When a user assigns a shortcut that's already in use,
 * this class identifies the conflict and suggests resolution.
 */
class ChordConflictDetector(
    private val chordRegistry: ChordRegistry = ChordRegistry.getInstance()
) {

    /**
     * Result of a conflict check.
     */
    data class ConflictResult(
        val hasConflict: Boolean,
        val conflictingActions: List<ChordAction> = emptyList()
    )

    /**
     * Checks if assigning a chord to an action would create a conflict.
     */
    fun checkConflict(
        baseKeyCode: Int,
        secondKeyCode: Int,
        context: PanelContext,
        actionId: String
    ): ConflictResult {
        val conflicts = chordRegistry.getConflicts(baseKeyCode, secondKeyCode, context)
        
        // Filter out the action being reassigned
        val relevantConflicts = conflicts.filter { it.actionId != actionId }
        
        return ConflictResult(
            hasConflict = relevantConflicts.isNotEmpty(),
            conflictingActions = relevantConflicts
        )
    }

    /**
     * Checks if a chord is used anywhere (any context).
     */
    fun isChordGloballyUsed(baseKeyCode: Int, secondKeyCode: Int): Boolean {
        return chordRegistry.isChordUsed(baseKeyCode, secondKeyCode)
    }

    /**
     * Gets all actions that use a given chord in any context.
     */
    fun getChordUsage(baseKeyCode: Int, secondKeyCode: Int): List<ChordAction> {
        val usage = mutableListOf<ChordAction>()
        for (context in PanelContext.entries) {
            chordRegistry.getConflicts(baseKeyCode, secondKeyCode, context)
                .filter { !usage.contains(it) }
                .let { usage.addAll(it) }
        }
        return usage
    }

    companion object {
        @JvmStatic val instance = ChordConflictDetector()
    }
}
