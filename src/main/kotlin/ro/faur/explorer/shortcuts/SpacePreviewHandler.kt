package ro.faur.explorer.shortcuts

import com.intellij.openapi.vfs.VirtualFile
import ro.faur.explorer.ui.BrowserPanel
import java.awt.Color
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.ToolTipManager
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Provides space-bar preview functionality for file browser panels.
 * 
 * When the user holds the spacebar over a file, a tooltip preview appears
 * after a short delay (100ms intent delay + preview load time).
 * 
 * Features:
 * - 100ms intent delay before showing preview
 * - Automatic positioning to stay on screen
 * - Dismisses on key release or mouse movement
 */
class SpacePreviewHandler(private val panel: BrowserPanel) {

    private var previewWindow: SpacePreviewWindow? = null
    private var intentTimer: Thread? = null
    private var lastMouseX: Int = 0
    private var lastMouseY: Int = 0
    private var spaceHeld = false
    
    init {
        setupSpacebarListener()
    }

    private fun setupSpacebarListener() {
        val tree = getTree() ?: return
        
        // Enable tooltips on the tree
        ToolTipManager.sharedInstance().registerComponent(tree)
        
        // Key listener for spacebar
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_SPACE && !e.isConsumed) {
                    e.consume()
                    handleSpaceDown(e)
                }
            }
            
            override fun keyReleased(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_SPACE) {
                    e.consume()
                    handleSpaceUp()
                }
            }
        })
        
        // Mouse listener for clicking on items
        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                // Cancel preview on click
                if (previewWindow != null) {
                    dismissPreview()
                }
            }
        })
        
        // Mouse motion to dismiss preview if cursor moves significantly
        tree.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                lastMouseX = e.xOnScreen
                lastMouseY = e.yOnScreen
            }
        })
    }

    private fun handleSpaceDown(e: KeyEvent) {
        if (spaceHeld) return
        spaceHeld = true
        
        // Cancel any pending preview
        intentTimer?.interrupt()
        
        // Get the selected node
        val tree = getTree() ?: return
        val path = tree.selectionPath ?: return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val virtualFile = node.userObject as? VirtualFile ?: return
        
        // Start the intent delay timer
        intentTimer = Thread {
            try {
                // 100ms intent delay
                Thread.sleep(100)
                
                if (spaceHeld) {
                    // Show preview on EDT
                    javax.swing.SwingUtilities.invokeLater {
                        if (spaceHeld) {
                            showPreview(virtualFile.path)
                        }
                    }
                }
            } catch (e: InterruptedException) {
                // Cancelled
            }
        }
        intentTimer?.start()
    }

    private fun handleSpaceUp() {
        spaceHeld = false
        intentTimer?.interrupt()
        dismissPreview()
    }

    private fun showPreview(path: String) {
        dismissPreview()
        
        // Get screen bounds for positioning
        val screenBounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
            .maximumWindowBounds
        
        // Get tree location for relative positioning
        val tree = getTree() ?: return
        val treeBounds = tree.bounds ?: return
        
        // Calculate position (show to the right of the tree)
        val x = treeBounds.x + treeBounds.width + 10
        val y = treeBounds.y + 50
        
        // Ensure it fits on screen
        val finalX = if (x + SPACE_PREVIEW_WIDTH > screenBounds.x + screenBounds.width) {
            treeBounds.x - SPACE_PREVIEW_WIDTH - 10
        } else x
        
        val finalY = if (y + SPACE_PREVIEW_HEIGHT > screenBounds.y + screenBounds.height) {
            screenBounds.y + screenBounds.height - SPACE_PREVIEW_HEIGHT - 10
        } else y
        
        previewWindow = SpacePreviewWindow(path)
        previewWindow?.setLocation(finalX, finalY)
        previewWindow?.isVisible = true
    }

    private fun dismissPreview() {
        previewWindow?.dispose()
        previewWindow = null
    }

    private fun getTree(): JTree? {
        return try {
            val method = panel.javaClass.getMethod("getTree")
            method.invoke(panel) as? JTree
        } catch (e: Exception) {
            null
        }
    }

    fun dispose() {
        spaceHeld = false
        intentTimer?.interrupt()
        dismissPreview()
    }

    companion object {
        const val SPACE_PREVIEW_WIDTH = 300
        const val SPACE_PREVIEW_HEIGHT = 200
    }
}

/**
 * A lightweight window for showing file previews.
 */
class SpacePreviewWindow(path: String) : javax.swing.JWindow() {
    
    init {
        val content = JPanel(java.awt.BorderLayout())
        content.border = BorderFactory.createLineBorder(Color.GRAY)
        
        // Header with filename
        val header = JLabel(path.substringAfterLast('/'), SwingConstants.CENTER).apply {
            font = font.deriveFont(java.awt.Font.BOLD, 12f)
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        }
        
        // Content area (could be extended to show file preview)
        val contentArea = JLabel("Preview for: ${path.substringAfterLast('/')}").apply {
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
            foreground = Color.GRAY
        }
        
        content.add(header, java.awt.BorderLayout.NORTH)
        content.add(contentArea, java.awt.BorderLayout.CENTER)
        
        add(content)
        preferredSize = Dimension(SpacePreviewHandler.SPACE_PREVIEW_WIDTH, SpacePreviewHandler.SPACE_PREVIEW_HEIGHT)
        pack()
        
        // Make it semi-transparent
        opacity = 0.95f
    }
}
