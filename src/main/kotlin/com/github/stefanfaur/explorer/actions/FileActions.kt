package com.github.stefanfaur.explorer.actions

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

object FileActions {

    fun createFile(parent: VirtualFile, name: String): VirtualFile? {
        if (parent.findChild(name) != null) return null
        return runWriteAction {
            parent.createChildData(this, name)
        }
    }

    fun createFolder(parent: VirtualFile, name: String): VirtualFile? {
        if (parent.findChild(name) != null) return null
        return runWriteAction {
            parent.createChildDirectory(this, name)
        }
    }

    fun rename(file: VirtualFile, newName: String) {
        runWriteAction {
            file.rename(this, newName)
        }
    }

    fun delete(file: VirtualFile) {
        runWriteAction {
            file.delete(this)
        }
    }

    fun copyTo(file: VirtualFile, destDir: VirtualFile): VirtualFile? {
        return runWriteAction {
            file.copy(this, destDir, file.name)
        }
    }

    fun moveTo(file: VirtualFile, destDir: VirtualFile) {
        runWriteAction {
            file.move(this, destDir)
        }
    }

    fun copyToClipboard(files: List<VirtualFile>) {
        val fileList = files.mapNotNull {
            java.io.File(it.path)
        }
        val pathsString = files.joinToString("\n") { it.path }
        val transferable = object : Transferable {
            private val flavors = arrayOf(DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor)
            override fun getTransferDataFlavors() = flavors
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor in flavors
            override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
                DataFlavor.javaFileListFlavor -> fileList
                DataFlavor.stringFlavor -> pathsString
                else -> throw java.awt.datatransfer.UnsupportedFlavorException(flavor)
            }
        }
        CopyPasteManager.getInstance().setContents(transferable)
    }

    fun copyPathToClipboard(file: VirtualFile) {
        val transferable = StringSelection(file.path)
        CopyPasteManager.getInstance().setContents(transferable)
    }
}
