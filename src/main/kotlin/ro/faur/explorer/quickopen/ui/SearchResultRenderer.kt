package ro.faur.explorer.quickopen.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import ro.faur.explorer.quickopen.model.CandidateType
import javax.swing.JList

class SearchResultRenderer : ColoredListCellRenderer<SearchResult>() {
    override fun customizeCellRenderer(
        list: JList<out SearchResult>,
        value: SearchResult?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        val result = value ?: return

        // Group header row
        if (result.isGroupHeader) {
            append(result.groupName, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            return
        }

        // Skeleton row (null scored, not a header)
        val scored = result.scored
        if (scored == null) {
            append("Loading...", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            return
        }

        val candidate = scored.candidate

        // Icon — use VirtualFile-based lookup for proper file-type icons
        icon = when (candidate.type) {
            CandidateType.DIRECTORY, CandidateType.BOOKMARK, CandidateType.RECENT -> {
                val vf = LocalFileSystem.getInstance().findFileByPath(candidate.fullPath)
                vf?.let { if (it.isDirectory) AllIcons.Nodes.Folder else it.fileType.icon }
                    ?: AllIcons.Nodes.Folder
            }
            CandidateType.FILE, CandidateType.OPEN_EDITOR -> {
                val vf = LocalFileSystem.getInstance().findFileByPath(candidate.fullPath)
                vf?.fileType?.icon ?: AllIcons.FileTypes.Any_type
            }
            CandidateType.ACTION -> AllIcons.Actions.Lightning
            CandidateType.CONTENT_MATCH -> AllIcons.Actions.Find
        }

        // Display name with match highlights
        val ranges = scored.matchedRanges
        if (ranges.isNotEmpty()) {
            var pos = 0
            val name = candidate.displayName
            for (range in ranges) {
                if (range.first > pos) append(name.substring(pos, range.first), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                append(name.substring(range.first, range.last + 1), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                pos = range.last + 1
            }
            if (pos < name.length) append(name.substring(pos), SimpleTextAttributes.REGULAR_ATTRIBUTES)
        } else {
            append(candidate.displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }

        // Dimmed parent path
        if (candidate.parentPath.isNotBlank()) {
            append("  ${candidate.parentPath}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }

        // Badges
        if (candidate.signals.isBookmarked) append("  ⭐", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        if (candidate.signals.isOpenInEditor) append("  📂", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        if (result.gitStatus.isNotEmpty()) append("  ${result.gitStatus}", SimpleTextAttributes.GRAYED_ATTRIBUTES)

        // Content snippet for CONTENT_MATCH results
        if (candidate.type == CandidateType.CONTENT_MATCH && !candidate.contentSnippet.isNullOrBlank()) {
            val snippet = candidate.contentSnippet.take(80)
            val matchRanges = candidate.contentMatchRanges
            append("  — ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            if (!matchRanges.isNullOrEmpty()) {
                val highlightAttr = SimpleTextAttributes(
                    SimpleTextAttributes.STYLE_BOLD,
                    com.intellij.ui.JBColor(0xE06C75, 0xFF6B6B)
                )
                var pos = 0
                for (range in matchRanges.sortedBy { it.first }) {
                    val start = range.first.coerceIn(0, snippet.length)
                    val end = (range.last + 1).coerceIn(0, snippet.length)
                    if (start > pos) append(snippet.substring(pos, start), SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    if (start < end) append(snippet.substring(start, end), highlightAttr)
                    pos = end
                    if (pos >= snippet.length) break
                }
                if (pos < snippet.length) append(snippet.substring(pos), SimpleTextAttributes.GRAYED_ATTRIBUTES)
            } else {
                append(snippet, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }
}
