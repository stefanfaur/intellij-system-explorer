package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ExplorerPanelToggleHiddenTest : BasePlatformTestCase() {

    fun `test ExplorerPanel has toggleHiddenFiles method`() {
        val methods = ro.faur.explorer.ui.ExplorerPanel::class.java.methods
        val toggle = methods.firstOrNull { it.name == "toggleHiddenFiles" }
        assertNotNull("ExplorerPanel must have a toggleHiddenFiles() method", toggle)
    }
}
