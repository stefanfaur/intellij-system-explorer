package ro.faur.explorer.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Manages a list of [BrowserPanel] instances and renders a compact tab strip for switching.
 *
 * Panel 0 is always the [LocalBrowserPanel] and cannot be closed.
 * Remote panels (index >= 1) show a × close button.
 *
 * Tab strip format:  [1 Local]  [● 2 dev-server ×]  [● 3 staging ×]
 */
class BrowserHost(localPanel: LocalBrowserPanel) : JPanel(BorderLayout()), Disposable {

    private val panels      = mutableListOf<BrowserPanel>()
    private val cardLayout  = CardLayout()
    private val cardPanel   = JPanel(cardLayout)
    private val tabStrip    = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
    private var activeIndex = 0

    val localPanel: LocalBrowserPanel get() = panels[0] as LocalBrowserPanel
    val activePanel: BrowserPanel     get() = panels[activeIndex]
    val panelCount: Int               get() = panels.size

    /** Returns a snapshot of the current panel list (index 0 = local). */
    fun getPanels(): List<BrowserPanel> = panels.toList()

    init {
        tabStrip.isOpaque = true
        tabStrip.background = JBUI.CurrentTheme.ToolWindow.headerBackground(true)

        add(tabStrip,   BorderLayout.NORTH)
        add(cardPanel,  BorderLayout.CENTER)

        addPanelInternal(localPanel)
        showActive()
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Adds a new panel (typically a [ro.faur.explorer.remote.ui.RemoteBrowserPanel]) and switches to it.
     * Returns the index of the new panel (for use in keyboard shortcut feedback).
     */
    fun addPanel(panel: BrowserPanel): Int {
        addPanelInternal(panel)
        val newIndex = panels.size - 1
        switchToPanel(newIndex)
        return newIndex
    }

    /**
     * Closes and disposes the panel at [index]. Index 0 (local) cannot be removed.
     * Focus returns to the panel immediately before [index].
     */
    fun removePanel(index: Int) {
        if (index <= 0 || index >= panels.size) return
        val panel = panels[index]
        panels.removeAt(index)
        cardPanel.remove(panel)
        Disposer.dispose(panel)
        activeIndex = (index - 1).coerceAtLeast(0)
        rebuildTabStrip()
        showActive()
    }

    /**
     * Switches the active panel to [index]. No-op if index is out of range.
     */
    fun switchToPanel(index: Int) {
        if (index !in panels.indices) return
        panels[activeIndex].isFocused = false
        activeIndex = index
        panels[activeIndex].isFocused = true
        rebuildTabStrip()
        showActive()
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private fun addPanelInternal(panel: BrowserPanel) {
        Disposer.register(this, panel)
        panels.add(panel)
        cardPanel.add(panel, cardKey(panel))
        rebuildTabStrip()
    }

    private fun cardKey(panel: BrowserPanel): String = System.identityHashCode(panel).toString()

    private fun showActive() {
        cardLayout.show(cardPanel, cardKey(panels[activeIndex]))
        cardPanel.revalidate()
        cardPanel.repaint()
    }

    private fun rebuildTabStrip() {
        tabStrip.removeAll()
        panels.forEachIndexed { i, panel ->
            tabStrip.add(buildTabComponent(i, panel))
        }
        tabStrip.revalidate()
        tabStrip.repaint()
    }

    private fun buildTabComponent(index: Int, panel: BrowserPanel): JPanel {
        val isActive = index == activeIndex
        val isRemote = index > 0
        val dot      = if (isRemote) "● " else ""
        val label    = "${index + 1} ${panel.panelLabel}"

        val btn = JButton("$dot$label").apply {
            isBorderPainted    = false
            isContentAreaFilled = isActive
            isFocusPainted     = false
            font               = font.deriveFont(if (isActive) java.awt.Font.BOLD else java.awt.Font.PLAIN)
            background         = if (isActive) JBUI.CurrentTheme.ActionButton.pressedBackground() else null
            foreground         = if (isActive) JBUI.CurrentTheme.Label.foreground() else JBUI.CurrentTheme.Label.disabledForeground()
            toolTipText        = panel.panelLabel
            addActionListener  { switchToPanel(index) }
        }

        val wrapper = JPanel(FlowLayout(FlowLayout.LEFT, 2, 1)).apply { isOpaque = false }
        wrapper.add(btn)

        if (isRemote) {
            val closeBtn = JButton("×").apply {
                isBorderPainted     = false
                isContentAreaFilled = false
                isFocusPainted      = false
                foreground          = Color.GRAY
                toolTipText         = "Close ${panel.panelLabel}"
                font                = font.deriveFont(11f)
                addActionListener   { removePanel(index) }
            }
            wrapper.add(closeBtn)
        }

        return wrapper
    }

    override fun dispose() {
        // Child panels are registered as disposables; Disposer handles them
    }
}
