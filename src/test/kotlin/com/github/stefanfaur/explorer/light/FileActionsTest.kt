package com.github.stefanfaur.explorer.light

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.stefanfaur.explorer.actions.FileActions

class FileActionsTest : BasePlatformTestCase() {

    private lateinit var testRoot: VirtualFile

    override fun setUp() {
        super.setUp()
        testRoot = runWriteActionAndWait {
            myFixture.tempDirFixture.findOrCreateDir("testRoot")
        }
    }

    fun `test create new file`() {
        val created = FileActions.createFile(testRoot, "newfile.txt")

        assertNotNull(created)
        assertEquals("newfile.txt", created!!.name)
        assertTrue(created.exists())
        assertFalse(created.isDirectory)
    }

    fun `test create new file in subdirectory`() {
        val subdir = runWriteActionAndWait {
            testRoot.createChildDirectory(this, "sub")
        }

        val created = FileActions.createFile(subdir, "nested.txt")

        assertNotNull(created)
        assertEquals("nested.txt", created!!.name)
        assertEquals(subdir, created.parent)
    }

    fun `test create new folder`() {
        val created = FileActions.createFolder(testRoot, "newfolder")

        assertNotNull(created)
        assertEquals("newfolder", created!!.name)
        assertTrue(created.isDirectory)
    }

    fun `test rename file`() {
        val file = runWriteActionAndWait {
            testRoot.createChildData(this, "original.txt")
        }

        FileActions.rename(file, "renamed.txt")

        assertEquals("renamed.txt", file.name)
    }

    fun `test delete file`() {
        val file = runWriteActionAndWait {
            testRoot.createChildData(this, "todelete.txt")
        }
        val path = file.path

        FileActions.delete(file)

        assertNull(testRoot.findChild("todelete.txt"))
    }

    fun `test copy file to directory`() {
        val file = runWriteActionAndWait {
            val f = testRoot.createChildData(this, "source.txt")
            f.setBinaryContent("hello".toByteArray())
            f
        }
        val destDir = runWriteActionAndWait {
            testRoot.createChildDirectory(this, "dest")
        }

        val copied = FileActions.copyTo(file, destDir)

        assertNotNull(copied)
        assertEquals("source.txt", copied!!.name)
        assertEquals(destDir, copied.parent)
        assertEquals("hello", String(copied.contentsToByteArray()))
        // Original still exists
        assertTrue(file.exists())
    }

    fun `test move file to directory`() {
        val file = runWriteActionAndWait {
            val f = testRoot.createChildData(this, "tomove.txt")
            f.setBinaryContent("content".toByteArray())
            f
        }
        val destDir = runWriteActionAndWait {
            testRoot.createChildDirectory(this, "dest")
        }

        FileActions.moveTo(file, destDir)

        assertNotNull(destDir.findChild("tomove.txt"))
        assertNull(testRoot.findChild("tomove.txt"))
        assertEquals("content", String(destDir.findChild("tomove.txt")!!.contentsToByteArray()))
    }

    fun `test create file with existing name returns null`() {
        runWriteActionAndWait {
            testRoot.createChildData(this, "exists.txt")
        }

        val result = FileActions.createFile(testRoot, "exists.txt")

        assertNull(result)
    }

    fun `test create folder with existing name returns null`() {
        runWriteActionAndWait {
            testRoot.createChildDirectory(this, "exists")
        }

        val result = FileActions.createFolder(testRoot, "exists")

        assertNull(result)
    }
}
