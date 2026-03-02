package ro.faur.explorer.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import ro.faur.explorer.quickopen.aliases.TeleportAliasStore
import ro.faur.explorer.quickopen.index.IndexRegistry
import ro.faur.explorer.quickopen.ui.IndexBrowserDialog
import ro.faur.explorer.quickopen.backend.NucleoNative
import ro.faur.explorer.quickopen.backend.RankerSelector
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.table.DefaultTableModel

class QuickOpenConfigurable : Configurable {

    // Index & Limits
    private val maxIndexSizeSpinner = JSpinner(SpinnerNumberModel(50_000, 1_000, 500_000, 1_000))
    private val maxDisplayedResultsSpinner = JSpinner(SpinnerNumberModel(50, 5, 500, 5))
    private val speedDialCountSpinner = JSpinner(SpinnerNumberModel(6, 1, 20, 1))
    private val recentQueryHistorySpinner = JSpinner(SpinnerNumberModel(20, 5, 100, 5))
    private val indexTimeoutSpinner = JSpinner(SpinnerNumberModel(10, 1, 120, 1))

    // ripgrep
    private lateinit var ripgrepPathField: TextFieldWithBrowseButton
    private val maxContentResultsSpinner = JSpinner(SpinnerNumberModel(200, 10, 2000, 10))
    private val ripgrepHiddenCheckBox = JBCheckBox("Search hidden files (--hidden)")
    private val ripgrepFollowCheckBox = JBCheckBox("Follow symlinks (--follow)")
    private val ripgrepIgnoreCheckBox = JBCheckBox("Respect .gitignore (uncheck to add --no-ignore)", true)
    private val ripgrepMaxDepthSpinner = JSpinner(SpinnerNumberModel(0, 0, 50, 1))
    private val ripgrepExtraFlagsField = JTextField()
    private val useRipgrepCheckBox = JBCheckBox("Use ripgrep for external paths (outside project)")
    private val contentSearchCheckBox = JBCheckBox("Enable /: content search mode")

    // Scoring
    private val halfLifeSpinner = JSpinner(SpinnerNumberModel(24.0, 1.0, 168.0, 1.0))
    private val recencyWeightSlider = JSlider(0, 100, 60)
    private val scorerDebugCheckBox = JBCheckBox("Show scorer debug scores in results")
    private lateinit var scorerStatusLabel: JBLabel

    // Lucene index
    private val luceneThresholdSpinner = JSpinner(SpinnerNumberModel(5_000, 100, 500_000, 1_000))
    private val luceneExtAllowlistField = JTextField()
    private val luceneMaxSizeSpinner = JSpinner(SpinnerNumberModel(500, 50, 10_000, 50))
    private val luceneEvictionSpinner = JSpinner(SpinnerNumberModel(30, 1, 365, 1))

    // Index health
    private lateinit var healthTableModel: DefaultTableModel
    private lateinit var healthTable: JBTable
    private lateinit var rebuildButton: javax.swing.JButton
    private var rebuildProgressLabel: JBLabel? = null
    private var rebuildTimer: javax.swing.Timer? = null

    // Aliases
    private lateinit var aliasTableModel: DefaultTableModel
    private lateinit var aliasTable: JBTable

    private var myPanel: JComponent? = null

    override fun getDisplayName(): String = "Quick Open"

    override fun createComponent(): JComponent {
        ripgrepPathField = TextFieldWithBrowseButton()
        ripgrepPathField.addBrowseFolderListener(
            "Select ripgrep executable", "Path to rg binary (leave blank to use PATH)",
            null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        )
        ripgrepExtraFlagsField.preferredSize = Dimension(300, ripgrepExtraFlagsField.preferredSize.height)
        luceneExtAllowlistField.preferredSize = Dimension(400, luceneExtAllowlistField.preferredSize.height)

        val rankerName = try { RankerSelector.active.name } catch (_: Exception) { "Unknown" }
        val available = NucleoNative.isAvailable
        val scorerText = if (available) "Fuzzy Scorer: $rankerName  \u2713 loaded"
                         else "Fuzzy Scorer: $rankerName (nucleo unavailable, using fallback)"
        scorerStatusLabel = JBLabel(scorerText).apply {
            foreground = if (available) JBColor.GREEN.darker() else JBColor.foreground()
        }

        aliasTableModel = DefaultTableModel(arrayOf("Alias", "Path"), 0)
        aliasTable = JBTable(aliasTableModel).apply {
            preferredScrollableViewportSize = Dimension(400, 120)
            putClientProperty("terminateEditOnFocusLost", true)
        }
        val aliasDecorator = ToolbarDecorator.createDecorator(aliasTable)
            .setAddAction { aliasTableModel.addRow(arrayOf("", "")) }
            .setRemoveAction { val r = aliasTable.selectedRow; if (r >= 0) aliasTableModel.removeRow(r) }
            .createPanel()

        recencyWeightSlider.paintTicks = true
        recencyWeightSlider.paintLabels = true
        recencyWeightSlider.majorTickSpacing = 25

        myPanel = panel {
            group("Index & Limits") {
                row("Max index size (files):") { cell(maxIndexSizeSpinner) }
                    .rowComment("Hard cap on in-memory file index. Higher = more RAM usage.")
                row("Max displayed results:") { cell(maxDisplayedResultsSpinner) }
                    .rowComment("How many ranked candidates appear in the results list.")
                row("Speed-dial count:") { cell(speedDialCountSpinner) }
                    .rowComment("Frecency shortcuts shown on empty query.")
                row("Recent query history:") { cell(recentQueryHistorySpinner) }
                    .rowComment("How many past queries Ctrl+R cycles through.")
                row("Enumeration timeout (s):") { cell(indexTimeoutSpinner) }
                    .rowComment("Kill file enumeration after this many seconds.")
            }
            group("Content Search \u2014 ripgrep") {
                row("ripgrep path:") { cell(ripgrepPathField).align(AlignX.FILL) }
                    .rowComment("Leave blank to detect from PATH.")
                row("Max content results:") { cell(maxContentResultsSpinner) }
                    .rowComment("Max matches returned by /: content search.")
                row { cell(ripgrepHiddenCheckBox) }
                row { cell(ripgrepFollowCheckBox) }
                row { cell(ripgrepIgnoreCheckBox) }
                row("Max search depth (0=unlimited):") { cell(ripgrepMaxDepthSpinner) }
                row("Additional flags:") { cell(ripgrepExtraFlagsField).align(AlignX.FILL) }
                    .rowComment("Raw flags appended to every ripgrep invocation.")
                row { cell(useRipgrepCheckBox) }
                row { cell(contentSearchCheckBox) }
            }
            group("Scoring & Frecency") {
                row { cell(scorerStatusLabel) }
                row("Recency decay half-life (hours):") { cell(halfLifeSpinner) }
                    .rowComment("Lower = favor recently used items more strongly.")
                row("Recency vs frequency weight:") { cell(recencyWeightSlider) }
                    .rowComment("Left (0) = pure frequency. Right (100) = pure recency.")
                row { cell(scorerDebugCheckBox) }
            }
            group("Teleport Aliases") {
                row { cell(aliasDecorator).align(AlignX.FILL) }
            }
            group("Lucene Index") {
                row("Hybrid mode threshold (files):") { cell(luceneThresholdSpinner) }
                    .rowComment("Roots with more files than this use the Lucene index instead of live ripgrep.")
                row("Content-indexed extensions:") { cell(luceneExtAllowlistField).align(AlignX.FILL) }
                    .rowComment("Comma-separated list of extensions (no dots) to index for content search.")
                row("Max index size per root (MB):") { cell(luceneMaxSizeSpinner) }
                    .rowComment("Content indexing stops when this limit is reached; path indexing continues.")
                row("Evict unused index after (days):") { cell(luceneEvictionSpinner) }
                    .rowComment("Index for a root not opened in this many days is automatically deleted.")
            }
            group("Lucene Index Health") {
                row {
                    healthTableModel = DefaultTableModel(arrayOf("Root", "Docs", "Size", "Last Built"), 0)
                    healthTable = JBTable(healthTableModel).apply {
                        preferredScrollableViewportSize = java.awt.Dimension(600, 120)
                        setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION)
                    }
                    cell(com.intellij.ui.components.JBScrollPane(healthTable)).align(AlignX.FILL)
                }
                row {
                    rebuildButton = javax.swing.JButton("Rebuild Selected Index").also { btn ->
                        btn.addActionListener { onRebuildClicked() }
                    }
                    cell(rebuildButton)
                    rebuildProgressLabel = JBLabel("").also { lbl -> cell(lbl) }
                }
                row {
                    button("Browse Index...") { onBrowseClicked() }
                    button("Clear All Index Caches") { onClearAllClicked() }
                }
            }
        }
        reset()
        refreshHealthTable()
        return myPanel!!
    }

    override fun isModified(): Boolean {
        val s = QuickOpenSettings.getInstance().state
        return maxIndexSizeSpinner.value as Int != s.maxIndexSize
            || maxDisplayedResultsSpinner.value as Int != s.maxDisplayedResults
            || speedDialCountSpinner.value as Int != s.speedDialCount
            || recentQueryHistorySpinner.value as Int != s.recentQueryHistory
            || indexTimeoutSpinner.value as Int != s.indexEnumerationTimeoutSec
            || (if (::ripgrepPathField.isInitialized) ripgrepPathField.text else "") != s.ripgrepPath
            || maxContentResultsSpinner.value as Int != s.maxContentResults
            || ripgrepHiddenCheckBox.isSelected != s.ripgrepSearchHidden
            || ripgrepFollowCheckBox.isSelected != s.ripgrepFollowSymlinks
            || ripgrepIgnoreCheckBox.isSelected != s.ripgrepRespectIgnore
            || ripgrepMaxDepthSpinner.value as Int != s.ripgrepMaxDepth
            || ripgrepExtraFlagsField.text != s.ripgrepExtraFlags
            || useRipgrepCheckBox.isSelected != s.useRipgrepForExternalPaths
            || contentSearchCheckBox.isSelected != s.contentSearchEnabled
            || (halfLifeSpinner.value as Double) != s.frecencyHalfLifeHours
            || recencyWeightSlider.value != s.frecencyRecencyWeight
            || scorerDebugCheckBox.isSelected != s.showScorerDebug
            || luceneThresholdSpinner.value as Int != s.luceneHybridThreshold
            || luceneExtAllowlistField.text != s.luceneExtensionAllowlist
            || luceneMaxSizeSpinner.value as Int != s.luceneMaxIndexSizeMb
            || luceneEvictionSpinner.value as Int != s.luceneEvictionDays
            || aliasesModified()
    }

    override fun apply() {
        val s = QuickOpenSettings.getInstance().state
        s.maxIndexSize = maxIndexSizeSpinner.value as Int
        s.maxDisplayedResults = maxDisplayedResultsSpinner.value as Int
        s.speedDialCount = speedDialCountSpinner.value as Int
        s.recentQueryHistory = recentQueryHistorySpinner.value as Int
        s.indexEnumerationTimeoutSec = indexTimeoutSpinner.value as Int
        if (::ripgrepPathField.isInitialized) s.ripgrepPath = ripgrepPathField.text
        s.maxContentResults = maxContentResultsSpinner.value as Int
        s.ripgrepSearchHidden = ripgrepHiddenCheckBox.isSelected
        s.ripgrepFollowSymlinks = ripgrepFollowCheckBox.isSelected
        s.ripgrepRespectIgnore = ripgrepIgnoreCheckBox.isSelected
        s.ripgrepMaxDepth = ripgrepMaxDepthSpinner.value as Int
        s.ripgrepExtraFlags = ripgrepExtraFlagsField.text
        s.useRipgrepForExternalPaths = useRipgrepCheckBox.isSelected
        s.contentSearchEnabled = contentSearchCheckBox.isSelected
        s.frecencyHalfLifeHours = halfLifeSpinner.value as Double
        s.frecencyRecencyWeight = recencyWeightSlider.value
        s.showScorerDebug = scorerDebugCheckBox.isSelected
        s.luceneHybridThreshold = luceneThresholdSpinner.value as Int
        s.luceneExtensionAllowlist = luceneExtAllowlistField.text
        s.luceneMaxIndexSizeMb = luceneMaxSizeSpinner.value as Int
        s.luceneEvictionDays = luceneEvictionSpinner.value as Int
        applyAliases()
    }

    override fun reset() {
        val s = QuickOpenSettings.getInstance().state
        maxIndexSizeSpinner.value = s.maxIndexSize
        maxDisplayedResultsSpinner.value = s.maxDisplayedResults
        speedDialCountSpinner.value = s.speedDialCount
        recentQueryHistorySpinner.value = s.recentQueryHistory
        indexTimeoutSpinner.value = s.indexEnumerationTimeoutSec
        if (::ripgrepPathField.isInitialized) ripgrepPathField.text = s.ripgrepPath
        maxContentResultsSpinner.value = s.maxContentResults
        ripgrepHiddenCheckBox.isSelected = s.ripgrepSearchHidden
        ripgrepFollowCheckBox.isSelected = s.ripgrepFollowSymlinks
        ripgrepIgnoreCheckBox.isSelected = s.ripgrepRespectIgnore
        ripgrepMaxDepthSpinner.value = s.ripgrepMaxDepth
        ripgrepExtraFlagsField.text = s.ripgrepExtraFlags
        useRipgrepCheckBox.isSelected = s.useRipgrepForExternalPaths
        contentSearchCheckBox.isSelected = s.contentSearchEnabled
        halfLifeSpinner.value = s.frecencyHalfLifeHours
        recencyWeightSlider.value = s.frecencyRecencyWeight
        scorerDebugCheckBox.isSelected = s.showScorerDebug
        luceneThresholdSpinner.value = s.luceneHybridThreshold
        luceneExtAllowlistField.text = s.luceneExtensionAllowlist
        luceneMaxSizeSpinner.value = s.luceneMaxIndexSizeMb
        luceneEvictionSpinner.value = s.luceneEvictionDays
        if (::aliasTableModel.isInitialized) resetAliases()
    }

    private fun aliasesModified(): Boolean {
        if (!::aliasTableModel.isInitialized) return false
        val storeAliases = try { TeleportAliasStore.getInstance().getAliases() } catch (_: Exception) { return false }
        val tableAliases = collectAliases()
        return tableAliases.size != storeAliases.size || tableAliases.any { (k, v) -> storeAliases[k] != v }
    }

    private fun collectAliases(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (row in 0 until aliasTableModel.rowCount) {
            val name = (aliasTableModel.getValueAt(row, 0) as? String)?.trim() ?: continue
            val path = (aliasTableModel.getValueAt(row, 1) as? String)?.trim() ?: continue
            if (name.isNotBlank()) result[name] = path
        }
        return result
    }

    private fun applyAliases() {
        if (!::aliasTableModel.isInitialized) return
        try {
            val store = TeleportAliasStore.getInstance()
            val existing = store.getAliases().keys.toSet()
            val newAliases = collectAliases()
            existing.filter { !newAliases.containsKey(it) }.forEach { store.removeAlias(it) }
            newAliases.forEach { (name, path) -> store.addAlias(name, path) }
        } catch (_: Exception) {}
    }

    private fun resetAliases() {
        while (aliasTableModel.rowCount > 0) aliasTableModel.removeRow(0)
        try {
            TeleportAliasStore.getInstance().getAliases().forEach { (name, path) ->
                aliasTableModel.addRow(arrayOf(name, path))
            }
        } catch (_: Exception) {}
    }

    private fun refreshHealthTable() {
        if (!::healthTableModel.isInitialized) return
        while (healthTableModel.rowCount > 0) healthTableModel.removeRow(0)
        try {
            val roots = IndexRegistry.listIndexedRoots()
            val home = System.getProperty("user.home")
            roots.forEach { (root, stats) ->
                val shortRoot = root.replace(home, "~")
                val sizeStr = when {
                    stats.diskSizeBytes < 1024 -> "${stats.diskSizeBytes} B"
                    stats.diskSizeBytes < 1024 * 1024 -> "${"%.1f".format(stats.diskSizeBytes / 1024.0)} KB"
                    else -> "${"%.1f".format(stats.diskSizeBytes / (1024.0 * 1024))} MB"
                }
                val builtStr = if (stats.lastBuiltMs == 0L) "—"
                               else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(stats.lastBuiltMs))
                healthTableModel.addRow(arrayOf(shortRoot, stats.docCount, sizeStr, builtStr))
            }
        } catch (_: Exception) {}
    }

    private fun selectedRoot(): String? {
        val row = if (::healthTable.isInitialized) healthTable.selectedRow else -1
        if (row < 0) return null
        val shortRoot = healthTableModel.getValueAt(row, 0) as? String ?: return null
        val home = System.getProperty("user.home")
        return shortRoot.replace("~", home)
    }

    private fun onRebuildClicked() {
        val root = selectedRoot() ?: run {
            com.intellij.openapi.ui.Messages.showInfoMessage("Select a root in the table first.", "Rebuild Index")
            return
        }
        val settings = try { QuickOpenSettings.getInstance().state } catch (_: Exception) { return }
        rebuildButton.isEnabled = false
        rebuildProgressLabel?.text = "  Rebuilding..."
        IndexRegistry.rebuild(root, settings) {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                rebuildTimer?.stop()
                rebuildButton.isEnabled = true
                rebuildProgressLabel?.text = "  Done."
                refreshHealthTable()
            }
        }
        rebuildTimer?.stop()
        rebuildTimer = javax.swing.Timer(500) {
            val progress = IndexRegistry.getBuildProgress(root)
            rebuildProgressLabel?.text = "  Rebuilding... ($progress files)"
        }.also { it.start() }
    }

    private fun onBrowseClicked() {
        val root = selectedRoot() ?: run {
            com.intellij.openapi.ui.Messages.showInfoMessage("Select a root in the table first.", "Browse Index")
            return
        }
        val manager = IndexRegistry.getManager(root) ?: run {
            com.intellij.openapi.ui.Messages.showInfoMessage("No ready index for this root.", "Browse Index")
            return
        }
        IndexBrowserDialog(manager, root).show()
    }

    private fun onClearAllClicked() {
        val indexDir = java.io.File(com.intellij.openapi.application.PathManager.getSystemPath(), "caches/explorer-index")
        if (indexDir.exists()) {
            indexDir.deleteRecursively()
            com.intellij.openapi.ui.Messages.showInfoMessage(
                "Index caches cleared. They will be rebuilt on next QuickOpen.", "Clear Index"
            )
        } else {
            com.intellij.openapi.ui.Messages.showInfoMessage("No index caches found.", "Clear Index")
        }
        refreshHealthTable()
    }

    override fun disposeUIResources() { myPanel = null }
}
