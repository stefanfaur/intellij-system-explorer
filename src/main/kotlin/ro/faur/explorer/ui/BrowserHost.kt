package ro.faur.explorer.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.event.MouseWheelListener
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.JScrollPane

/**
 * Manages a list of [BrowserPanel] instances and renders a compact tab strip for switching.
 *
 * Panel 0 is always the [LocalBrowserPanel] and cannot be closed.
 * Remote panels (index >= 1) show a × close button.
 *
 * The [tabScrollPane] is designed to be embedded in the header row of [ExplorerPanel]
 * alongside the Connect and Settings buttons. It scrolls horizontally on mouse wheel
 * without showing a scrollbar.
 *
 * Tab strip format:  [1 Local]  [● 2 dev-server ×]  [● 3 staging ×]
 */
class BrowserHost(localPanel: LocalBrowserPanel) : JPanel(BorderLayout()), Disposable {

    private val panels      = mutableListOf<BrowserPanel>()
    private val cardLayout  = CardLayout()
    private val cardPanel   = JPanel(cardLayout)
    private val tabInner    = JPanel().apply { layout = BoxLayout(this, BoxLayout.X_AXIS) }
    private var activeIndex = 0

    val localPanel: LocalBrowserPanel get() = panels[0] as LocalBrowserPanel
    val activePanel: BrowserPanel     get() = panels[activeIndex]
    val panelCount: Int               get() = panels.size

    /** Returns a snapshot of the current panel list (index 0 = local). */
    fun getPanels(): List<BrowserPanel> = panels.toList()

    /**
     * Scrollable tab strip — embed this in the header row of [ExplorerPanel].
     * Scrolls horizontally via mouse wheel; scrollbar is never rendered.
     */
    val tabScrollPane: JScrollPane = JScrollPane(tabInner).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy   = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        border     = null
        isOpaque   = false
        viewport.isOpaque = false
    }

    init {
        tabInner.isOpaque = true
        tabInner.background = JBUI.CurrentTheme.ToolWindow.headerBackground(true)

        // Forward mouse-wheel events to the hidden horizontal scroll bar
        val scroller = MouseWheelListener { e ->
            val bar = tabScrollPane.horizontalScrollBar
            bar.value = (bar.value + e.wheelRotation.toInt() * bar.blockIncrement)
                .coerceIn(bar.minimum, bar.maximum)
        }
        tabScrollPane.addMouseWheelListener(scroller)
        tabInner.addMouseWheelListener(scroller)

        add(cardPanel, BorderLayout.CENTER)

        addPanelInternal(localPanel)
        showActive()
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Adds a new panel (typically a [ro.faur.explorer.remote.ui.RemoteBrowserPanel]) and switches to it.
     */
    fun addPanel(panel: BrowserPanel): Int {
        addPanelInternal(panel)
        val newIndex = panels.size - 1
        switchToPanel(newIndex)
        return newIndex
    }

    /**
     * Closes and disposes the panel at [index]. Index 0 (local) cannot be removed.
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
     * Switches the active panel to [index]. No-op if out of range.
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
        tabInner.removeAll()
        panels.forEachIndexed { i, panel -> tabInner.add(buildTab(i, panel)) }
        tabInner.add(Box.createHorizontalGlue())   // push tabs to the left
        tabInner.revalidate()
        tabInner.repaint()
    }

    private fun buildTab(index: Int, panel: BrowserPanel): JComponent {
        val isActive  = index == activeIndex
        val isRemote  = index > 0
        val tabFont   = font.deriveFont(11f)
        val activeBg  = JBUI.CurrentTheme.ActionButton.pressedBackground()
        val fgActive  = JBUI.CurrentTheme.Label.foreground()
        val fgDim     = JBUI.CurrentTheme.Label.disabledForeground()

        val dot   = if (isRemote) "● " else ""
        val label = "$dot${index + 1} ${panel.panelLabel}"

        val nameBtn = JButton(label).apply {
            isBorderPainted     = false
            isContentAreaFilled = isActive
            isFocusPainted      = false
            font                = tabFont
            margin              = JBUI.insets(2, 8, 2, if (isRemote) 2 else 8)
            foreground          = if (isActive) fgActive else fgDim
            if (isActive) background = activeBg
            toolTipText         = panel.panelLabel
            addActionListener   { switchToPanel(index) }
        }

        if (!isRemote) return nameBtn

        val closeBtn = JButton("×").apply {
            isBorderPainted     = false
            isContentAreaFilled = false
            isFocusPainted      = false
            font                = tabFont
            margin              = JBUI.insets(2, 2, 2, 6)
            foreground          = fgDim
            toolTipText         = "Close ${panel.panelLabel}"
            preferredSize       = Dimension(18, preferredSize.height.coerceAtLeast(1))
            maximumSize         = Dimension(18, Int.MAX_VALUE)
            addActionListener   { removePanel(index) }
        }

        // Group label + close into one visual unit sharing the active background
        return JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = isActive
            if (isActive) background = activeBg
            add(nameBtn)
            add(closeBtn)
        }
    }

    override fun dispose() {
        // Child panels disposed via Disposer.register
    }
}
