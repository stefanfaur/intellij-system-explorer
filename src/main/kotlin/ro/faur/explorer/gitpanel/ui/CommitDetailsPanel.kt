package ro.faur.explorer.gitpanel.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import ro.faur.explorer.gitpanel.CommitInfo
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class CommitDetailsPanel : JPanel() {

    companion object {
        private const val CARD_DETAILS = "DETAILS"
        private const val CARD_EDITOR  = "EDITOR"
    }

    private val cards = CardLayout()

    // ── DETAILS card ──────────────────────────────────────────────────────────
    private val detailsEditor = JEditorPane("text/html", "").apply {
        isEditable = false
        background = UIUtil.getPanelBackground()
    }

    // ── EDITOR card ───────────────────────────────────────────────────────────
    private val branchLabel = JLabel("Committing to: ⎇ …").apply {
        border = BorderFactory.createEmptyBorder(6, 8, 4, 8)
        font = font.deriveFont(java.awt.Font.BOLD)
    }

    private val messageArea = JTextArea(4, 30).apply {
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIUtil.getBoundsColor()),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        )
        text = ""
    }

    private val errorLabel = JLabel("").apply {
        foreground = java.awt.Color(0xCC, 0x33, 0x33)
        border = BorderFactory.createEmptyBorder(4, 8, 0, 8)
        isVisible = false
    }

    private val commitButton = JButton("Commit")
    private val commitPushButton = JButton("Commit & Push").apply {
        toolTipText = "Commit staged files and push to remote"
    }

    private var onAction: ((message: String, push: Boolean) -> Unit)? = null

    init {
        layout = cards

        // ── Build DETAILS card ────────────────────────────────────────────────
        val detailsCard = JPanel(BorderLayout())
        detailsCard.add(JBScrollPane(detailsEditor), BorderLayout.CENTER)
        add(detailsCard, CARD_DETAILS)

        // ── Build EDITOR card ─────────────────────────────────────────────────
        val editorCard = JPanel(BorderLayout())

        val topPanel = JPanel(BorderLayout())
        topPanel.add(branchLabel, BorderLayout.CENTER)
        topPanel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, UIUtil.getBoundsColor())

        val centerPanel = JPanel(BorderLayout())
        centerPanel.border = BorderFactory.createEmptyBorder(6, 8, 4, 8)
        centerPanel.add(JScrollPane(messageArea), BorderLayout.CENTER)
        centerPanel.add(errorLabel, BorderLayout.SOUTH)

        val buttonRow = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 6))
        buttonRow.add(commitButton)
        buttonRow.add(commitPushButton)

        editorCard.add(topPanel, BorderLayout.NORTH)
        editorCard.add(centerPanel, BorderLayout.CENTER)
        editorCard.add(buttonRow, BorderLayout.SOUTH)

        add(editorCard, CARD_EDITOR)

        // ── Wire buttons ──────────────────────────────────────────────────────
        commitButton.addActionListener { fireAction(push = false) }
        commitPushButton.addActionListener { fireAction(push = true) }

        // Show DETAILS by default
        setCommit(null)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun setCommit(info: CommitInfo?) {
        detailsEditor.background = UIUtil.getPanelBackground()
        detailsEditor.text = if (info == null) buildEmptyHtml() else buildHtml(info)
        detailsEditor.caretPosition = 0
        cards.show(this, CARD_DETAILS)
    }

    fun setWorkingTreeMode(branch: String?, onAction: (message: String, push: Boolean) -> Unit) {
        this.onAction = onAction
        branchLabel.text = "Committing to: ⎇ ${branch ?: "(detached HEAD)"}"
        messageArea.text = ""
        errorLabel.isVisible = false
        setButtonsEnabled(true)
        cards.show(this, CARD_EDITOR)
    }

    fun showError(message: String) {
        errorLabel.text = message
        errorLabel.isVisible = true
        setButtonsEnabled(true)
    }

    fun showProgress() {
        errorLabel.isVisible = false
        setButtonsEnabled(false)
    }

    fun clearEditor() {
        messageArea.text = ""
        errorLabel.isVisible = false
        setButtonsEnabled(true)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fireAction(push: Boolean) {
        val message = messageArea.text.trim()
        if (message.isEmpty()) {
            showError("Commit message cannot be empty.")
            return
        }
        errorLabel.isVisible = false
        onAction?.invoke(message, push)
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        commitButton.isEnabled = enabled
        commitPushButton.isEnabled = enabled
        if (!enabled) {
            commitButton.text = "Working…"
            commitPushButton.text = "Working…"
        } else {
            commitButton.text = "Commit"
            commitPushButton.text = "Commit & Push"
        }
    }

    private fun buildEmptyHtml(): String {
        val fg = ColorUtil.toHex(UIUtil.getInactiveTextColor())
        return """
            <html><body style="font-family:sans-serif; padding:8px; color:#$fg;">
            Select a commit to view details
            </body></html>
        """.trimIndent()
    }

    private fun buildHtml(info: CommitInfo): String {
        val fgHex    = ColorUtil.toHex(UIUtil.getLabelForeground())
        val mutedHex = ColorUtil.toHex(UIUtil.getInactiveTextColor())
        val bg       = ColorUtil.toHex(UIUtil.getPanelBackground())

        val dateStr = DateTimeFormatter
            .ofPattern("EEE, MMM d yyyy HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(info.date)

        return """
            <html><body style="font-family:sans-serif; padding:8px; color:#$fgHex; background:#$bg;">
            <div style="font-family:monospace; color:#$mutedHex; word-break:break-all;">${info.hash}</div>
            <div style="margin-top:4px;"><b>${escapeHtml(info.author)}</b> &lt;${escapeHtml(info.email)}&gt;</div>
            <div style="color:#$mutedHex;">$dateStr</div>
            <hr style="border:0; border-top:1px solid #$mutedHex; margin:6px 0;"/>
            <div style="font-weight:bold; margin-bottom:4px;">${escapeHtml(info.subject)}</div>
            <div style="white-space:pre-wrap; font-size:0.95em;">${escapeHtml(info.body)}</div>
            </body></html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")
}
