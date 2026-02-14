package com.github.stefanfaur.explorer.actions

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile

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
}
