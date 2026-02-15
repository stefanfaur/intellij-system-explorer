package com.github.stefanfaur.explorer.ui

import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.github.stefanfaur.explorer.model.Bookmark
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Sidebar panel displaying bookmarked filesystem locations.
 *
 * Uses a [JBList] to show bookmark names. When a bookmark is selected,
 * the [onBookmarkSelected] callback is invoked with the bookmark's path.
 */
class BookmarksPanel(private val onBookmarkSelected: (String) -> Unit) {

    private val listModel = DefaultListModel<String>()
    private val bookmarksList = JBList(listModel)
    private val bookmarkPaths = mutableListOf<String>()

    val component: JComponent

    init {
        bookmarksList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        bookmarksList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val idx = bookmarksList.selectedIndex
                if (idx >= 0 && idx < bookmarkPaths.size) {
                    onBookmarkSelected(bookmarkPaths[idx])
                }
            }
        }

        val panel = JPanel(BorderLayout())
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
}
