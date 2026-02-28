package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Phase 2 — Spec: plugin.xml action registration (Human Verification Item 6)
 *
 * The plan (02-03-PLAN.md) requires:
 *   - CompareWithAction registered under id "SystemExplorer.CompareWith"
 *   - Registered inside the "SystemExplorer.ActionGroup" group (context menu)
 *   - Class attribute points to ro.faur.explorer.actions.CompareWithAction
 *   - Text contains "Compare" (shown in right-click menu)
 *
 * These tests parse plugin.xml directly and verify the structural contract.
 * They do NOT require an IDE runtime — they run as pure JUnit 5 unit tests.
 */
class Phase2PluginXmlDiffActionsTest {

    private fun loadPluginXml(): org.w3c.dom.Document {
        val stream = javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml")
            ?: error("META-INF/plugin.xml not found on test classpath")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
    }

    // ── Action registration ───────────────────────────────────────────────────

    @Test
    fun `plugin xml declares SystemExplorer CompareWith action`() {
        val doc = loadPluginXml()
        val actions = doc.getElementsByTagName("action")
        val ids = (0 until actions.length).map { (actions.item(it) as Element).getAttribute("id") }
        assertTrue(
            ids.contains("SystemExplorer.CompareWith"),
            "plugin.xml must declare an action with id='SystemExplorer.CompareWith'. Found: $ids"
        )
    }

    @Test
    fun `CompareWith action class points to correct fully qualified name`() {
        val doc = loadPluginXml()
        val actions = doc.getElementsByTagName("action")
        for (i in 0 until actions.length) {
            val action = actions.item(i) as Element
            if (action.getAttribute("id") == "SystemExplorer.CompareWith") {
                assertEquals(
                    "ro.faur.explorer.actions.CompareWithAction",
                    action.getAttribute("class"),
                    "CompareWith action 'class' attribute must be the fully-qualified class name"
                )
                return
            }
        }
        fail("SystemExplorer.CompareWith action not found in plugin.xml")
    }

    @Test
    fun `CompareWith action text mentions Compare`() {
        val doc = loadPluginXml()
        val actions = doc.getElementsByTagName("action")
        for (i in 0 until actions.length) {
            val action = actions.item(i) as Element
            if (action.getAttribute("id") == "SystemExplorer.CompareWith") {
                val text = action.getAttribute("text")
                assertTrue(
                    text.contains("Compare", ignoreCase = true),
                    "CompareWith action 'text' should mention Compare. Got: '$text'"
                )
                return
            }
        }
        fail("SystemExplorer.CompareWith action not found in plugin.xml")
    }

    // ── Action group membership ────────────────────────────────────────────────

    @Test
    fun `CompareWith action is nested inside SystemExplorer ActionGroup`() {
        val doc = loadPluginXml()
        val groups = doc.getElementsByTagName("group")
        for (i in 0 until groups.length) {
            val group = groups.item(i) as Element
            if (group.getAttribute("id") == "SystemExplorer.ActionGroup") {
                val nested = group.getElementsByTagName("action")
                val nestedIds = (0 until nested.length).map { (nested.item(it) as Element).getAttribute("id") }
                assertTrue(
                    nestedIds.contains("SystemExplorer.CompareWith"),
                    "SystemExplorer.CompareWith must be inside SystemExplorer.ActionGroup. Found in group: $nestedIds"
                )
                return
            }
        }
        fail("SystemExplorer.ActionGroup not found in plugin.xml")
    }

    @Test
    fun `SystemExplorer ActionGroup exists in plugin xml`() {
        val doc = loadPluginXml()
        val groups = doc.getElementsByTagName("group")
        val groupIds = (0 until groups.length).map { (groups.item(it) as Element).getAttribute("id") }
        assertTrue(
            groupIds.contains("SystemExplorer.ActionGroup"),
            "SystemExplorer.ActionGroup must be declared in plugin.xml. Found groups: $groupIds"
        )
    }
}
