package ro.faur.explorer.ui

import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Duration

class ExplorerToolWindowUITest : ExplorerUITestBase() {

    @Test
    fun `tool window opens and displays file tree`() {
        with(robot) {
            // Find and click the tool window stripe button
            val stripeButton = find<ComponentFixture>(
                byXpath("//div[@text='System Explorer']"),
                Duration.ofSeconds(10)
            )
            stripeButton.click()

            // Verify tree is visible
            waitFor(Duration.ofSeconds(5)) {
                findAll<JTreeFixture>(
                    byXpath("//div[@class='Tree']")
                ).isNotEmpty()
            }
        }
    }

    @Test
    fun `toolbar buttons are present`() {
        with(robot) {
            // Verify toolbar has expected buttons
            val toolbar = find<ComponentFixture>(
                byXpath("//div[@class='ActionToolbarImpl']"),
                Duration.ofSeconds(10)
            )
            assertNotNull(toolbar)

            // Check for Home button
            val homeButton = findAll<ComponentFixture>(
                byXpath("//div[@tooltiptext='Home']")
            )
            assertTrue(homeButton.isNotEmpty(), "Home button should exist")
        }
    }

    @Test
    fun `double-click folder expands it`() {
        with(robot) {
            val tree = find<JTreeFixture>(
                byXpath("//div[@class='Tree']"),
                Duration.ofSeconds(10)
            )

            // Find a folder node and double-click
            val firstFolder = tree.findAllText { it.text.isNotEmpty() }.firstOrNull()
            assertNotNull(firstFolder, "Tree should have at least one item")

            firstFolder?.doubleClick()

            // Wait for expansion
            waitFor(Duration.ofSeconds(3)) {
                tree.findAllText { it.text.isNotEmpty() }.size > 1
            }
        }
    }

    @Test
    fun `bookmarks panel is visible`() {
        with(robot) {
            val bookmarksPanel = findAll<ComponentFixture>(
                byXpath("//div[@accessiblename='Bookmarks']")
            )
            assertTrue(bookmarksPanel.isNotEmpty(), "Bookmarks panel should be visible")
        }
    }

    @Test
    fun `status bar shows selection info`() {
        with(robot) {
            val statusBar = findAll<ComponentFixture>(
                byXpath("//div[@class='JLabel'][contains(@text, 'folder') or contains(@text, 'file')]")
            )
            assertTrue(statusBar.isNotEmpty(), "Status bar should show file/folder counts")
        }
    }
}
