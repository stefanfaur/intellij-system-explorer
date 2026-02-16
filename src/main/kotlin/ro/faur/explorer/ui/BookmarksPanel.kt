package ro.faur.explorer.ui

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import ro.faur.explorer.model.Bookmark
import java.awt.BorderLayout
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * Sidebar panel displaying bookmarked filesystem locations.
 *
 * Uses a [JBList] to show bookmark names. When a bookmark is selected,
 * the [onBookmarkSelected] callback is invoked with the bookmark's path.
 *
 * Supports drag-and-drop reordering and right-click deletion.
 */
class BookmarksPanel(
    private val onBookmarkSelected: (String) -> Unit,
    private val onBookmarkMoved: ((Int, Int) -> Unit)? = null,
    private val onBookmarkDeleted: ((Int) -> Unit)? = null
) {

    private val listModel = DefaultListModel<String>()
    private val bookmarksList = JBList(listModel)
    private val bookmarkPaths = mutableListOf<String>()
    private var isDragging = false
    private var isUpdatingSelection = false

    val component: JComponent

    init {
        bookmarksList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        // Navigate on click — using MouseListener instead of ListSelectionListener
        // so that clicks in empty space (below all items) don't trigger navigation.
        // JBList.locationToIndex returns the nearest index even for empty space,
        // so we must verify the click is within actual cell bounds.
        bookmarksList.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showContextMenu(e)
                    return
                }
                // After JList's UI handler selects the nearest item,
                // clear the selection if the click was in empty space.
                val index = bookmarksList.locationToIndex(e.point)
                if (index >= 0) {
                    val cellBounds = bookmarksList.getCellBounds(index, index)
                    if (cellBounds == null || !cellBounds.contains(e.point)) {
                        bookmarksList.clearSelection()
                    }
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) showContextMenu(e)
            }

            override fun mouseClicked(e: MouseEvent) {
                if (isDragging || isUpdatingSelection || e.isPopupTrigger) return
                val index = bookmarksList.locationToIndex(e.point)
                if (index < 0 || index >= bookmarkPaths.size) return
                val cellBounds = bookmarksList.getCellBounds(index, index) ?: return
                if (!cellBounds.contains(e.point)) return
                onBookmarkSelected(bookmarkPaths[index])
            }
        })

        // Drag-and-drop reordering
        bookmarksList.dragEnabled = true
        bookmarksList.dropMode = DropMode.INSERT
        bookmarksList.transferHandler = BookmarkTransferHandler()

        // Custom renderer with left padding
        bookmarksList.cellRenderer = object : ColoredListCellRenderer<String>() {
            override fun customizeCellRenderer(
                list: JList<out String>,
                value: String,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                append(value)
                border = JBUI.Borders.empty(2, 4, 2, 4)
            }
        }

        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 2, 0, 2)
        panel.add(JBScrollPane(bookmarksList), BorderLayout.CENTER)
        component = panel
    }

    /**
     * Updates the displayed bookmarks list.
     */
    fun setBookmarks(bookmarks: List<Bookmark>) {
        listModel.clear()
        bookmarkPaths.clear()
        for (bookmark in bookmarks) {
            listModel.addElement(bookmark.name)
            bookmarkPaths.add(bookmark.path)
        }
    }

    /**
     * Highlights the bookmark whose path matches [path], or clears the selection
     * if no bookmark matches. Does NOT trigger the [onBookmarkSelected] callback.
     */
    fun highlightForPath(path: String) {
        isUpdatingSelection = true
        try {
            val index = bookmarkPaths.indexOf(path)
            if (index >= 0) {
                bookmarksList.selectedIndex = index
            } else {
                bookmarksList.clearSelection()
            }
        } finally {
            isUpdatingSelection = false
        }
    }

    private fun showContextMenu(e: MouseEvent) {
        val index = bookmarksList.locationToIndex(e.point)
        if (index < 0) return
        val bounds = bookmarksList.getCellBounds(index, index) ?: return
        if (!bounds.contains(e.point)) return

        val menu = JPopupMenu()
        menu.add(JMenuItem("Delete").apply {
            addActionListener {
                onBookmarkDeleted?.invoke(index)
            }
        })
        menu.show(bookmarksList, e.x, e.y)
    }

    private inner class BookmarkTransferHandler : TransferHandler() {
        private val flavor = DataFlavor.stringFlavor
        private var dragSourceIndex = -1

        override fun getSourceActions(c: JComponent) = MOVE

        override fun createTransferable(c: JComponent): Transferable? {
            dragSourceIndex = bookmarksList.selectedIndex
            if (dragSourceIndex < 0) return null
            isDragging = true
            return object : Transferable {
                override fun getTransferDataFlavors() = arrayOf(flavor)
                override fun isDataFlavorSupported(f: DataFlavor) = f == flavor
                override fun getTransferData(f: DataFlavor): Any = listModel[dragSourceIndex]
            }
        }

        override fun canImport(support: TransferSupport): Boolean {
            return support.isDrop && support.isDataFlavorSupported(flavor) && dragSourceIndex >= 0
        }

        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support)) return false
            val dl = support.dropLocation as? JList.DropLocation ?: return false
            val dropIndex = dl.index
            if (dragSourceIndex < 0 || dropIndex < 0) return false
            // No-op if dropping in same position or right after itself
            if (dragSourceIndex == dropIndex || dragSourceIndex + 1 == dropIndex) return false

            // Adjust for removal shift when dragging downward
            val targetIndex = if (dropIndex > dragSourceIndex) dropIndex - 1 else dropIndex

            // Update local model
            val name = listModel.remove(dragSourceIndex)
            listModel.add(targetIndex, name)
            val path = bookmarkPaths.removeAt(dragSourceIndex)
            bookmarkPaths.add(targetIndex, path)

            bookmarksList.selectedIndex = targetIndex

            // Persist via callback
            onBookmarkMoved?.invoke(dragSourceIndex, targetIndex)

            return true
        }

        override fun exportDone(source: JComponent?, data: Transferable?, action: Int) {
            dragSourceIndex = -1
            isDragging = false
        }
    }
}
