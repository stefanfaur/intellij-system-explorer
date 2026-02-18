package ro.faur.explorer.light

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.ExplorerToolWindowFactory
import ro.faur.explorer.actions.BackInExplorerAction
import ro.faur.explorer.actions.ExplorerActionUtil

class BackInExplorerActionTest : BasePlatformTestCase() {

    fun `test Back action navigates to previous history entry`() {
        val root = runWriteActionAndWait {
            myFixture.tempDirFixture.findOrCreateDir("backActionRoot")
        }
        val dirA = runWriteActionAndWait {
            root.createChildDirectory(this, "dirA")
        }
        val dirB = runWriteActionAndWait {
            root.createChildDirectory(this, "dirB")
        }

        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.registerToolWindow("System Explorer") { }
        ExplorerToolWindowFactory().createToolWindowContent(project, toolWindow)

        val panel = ExplorerActionUtil.activateAndFindPanel(project)
        assertNotNull(panel)

        panel!!.navigateTo(dirA.path)
        panel.navigateTo(dirB.path)
        assertEquals(dirB.path, panel.currentPath)

        val action = BackInExplorerAction()
        action.actionPerformed(createActionEvent(action))

        assertEquals(dirA.path, panel.currentPath)
    }

    private fun createActionEvent(action: BackInExplorerAction): AnActionEvent {
        val dataContext = DataContext { dataId ->
            if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
        }
        return AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext)
    }
}
