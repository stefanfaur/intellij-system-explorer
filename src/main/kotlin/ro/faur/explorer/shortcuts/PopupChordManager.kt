package ro.faur.explorer.shortcuts

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.JBPopup

/**
 * Manages chord detection for the Quick Open popup.
 * 
 * Tracks the active QuickOpenPanel instance and provides
 * chord state management when Quick Open is visible.
 */
class PopupChordManager(
    private val chordKeyAdapter: ChordKeyAdapter
) : Disposable {

    private var activePopup: JBPopup? = null

    /**
     * Tracks the active popup.
     */
    fun trackPopup(popup: JBPopup) {
        activePopup = popup
    }

    /**
     * Clears chord state.
     */
    fun clearChordState() {
        chordKeyAdapter.resetChordState()
    }

    /**
     * Checks if a popup is currently active.
     */
    fun isPopupActive(): Boolean = activePopup?.isVisible == true

    /**
     * Closes the active popup.
     */
    fun closePopup() {
        activePopup?.closeOk(null)
    }

    override fun dispose() {
        activePopup?.closeOk(null)
        activePopup = null
    }
}
