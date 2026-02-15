package com.github.stefanfaur.explorer.heavy

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.stefanfaur.explorer.actions.FileActions
import java.awt.datatransfer.DataFlavor

class ClipboardInteropTest : BasePlatformTestCase() {

    private lateinit var testRoot: VirtualFile

    override fun setUp() {
        super.setUp()
        testRoot = runWriteActionAndWait {
            myFixture.tempDirFixture.findOrCreateDir("clipboardTestRoot")
        }
    }

    fun `test copy file puts file reference on clipboard`() {
        val file = runWriteActionAndWait {
            testRoot.createChildData(this, "copied.txt").also {
                it.setBinaryContent("content".toByteArray())
            }
        }

        FileActions.copyToClipboard(listOf(file))

        val clipboard = CopyPasteManager.getInstance()
        assertTrue(clipboard.areDataFlavorsAvailable(DataFlavor.javaFileListFlavor))
    }

    fun `test copy path puts string on clipboard`() {
        val file = runWriteActionAndWait {
            testRoot.createChildData(this, "pathfile.txt")
        }

        FileActions.copyPathToClipboard(file)

        val clipboard = CopyPasteManager.getInstance()
        assertTrue(clipboard.areDataFlavorsAvailable(DataFlavor.stringFlavor))
        val contents = clipboard.contents
        assertNotNull(contents)
        val path = contents!!.getTransferData(DataFlavor.stringFlavor) as String
        assertTrue(path.endsWith("pathfile.txt"))
    }
}
