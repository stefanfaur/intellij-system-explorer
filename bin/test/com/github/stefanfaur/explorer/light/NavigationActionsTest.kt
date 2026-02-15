package com.github.stefanfaur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.stefanfaur.explorer.actions.NavigationActions

class NavigationActionsTest : BasePlatformTestCase() {

    fun `test goToParent from nested path returns parent`() {
        val result = NavigationActions.goToParent("/usr/local/bin")
        assertEquals("/usr/local", result)
    }

    fun `test goToParent from root returns root`() {
        val result = NavigationActions.goToParent("/")
        assertEquals("/", result)
    }

    fun `test goHome returns user home directory`() {
        val result = NavigationActions.goHome()
        assertEquals(System.getProperty("user.home"), result)
    }

    fun `test goToRoot returns filesystem root`() {
        val result = NavigationActions.goToRoot()
        assertEquals("/", result)
    }

    fun `test history tracks navigation`() {
        val nav = NavigationActions.NavigationHistory()
        nav.push("/first")
        nav.push("/second")
        nav.push("/third")

        assertEquals("/second", nav.back())
        assertEquals("/third", nav.forward())
    }

    fun `test history back at start returns null`() {
        val nav = NavigationActions.NavigationHistory()
        nav.push("/only")

        assertNull(nav.back())
    }

    fun `test history forward at end returns null`() {
        val nav = NavigationActions.NavigationHistory()
        nav.push("/only")

        assertNull(nav.forward())
    }

    fun `test history forward cleared after new push`() {
        val nav = NavigationActions.NavigationHistory()
        nav.push("/first")
        nav.push("/second")
        nav.push("/third")

        nav.back() // at /second
        nav.push("/new") // forward history cleared

        assertNull(nav.forward())
    }

    fun `test canGoBack and canGoForward`() {
        val nav = NavigationActions.NavigationHistory()
        assertFalse(nav.canGoBack)
        assertFalse(nav.canGoForward)

        nav.push("/a")
        assertFalse(nav.canGoBack)

        nav.push("/b")
        assertTrue(nav.canGoBack)
        assertFalse(nav.canGoForward)

        nav.back()
        assertFalse(nav.canGoBack)
        assertTrue(nav.canGoForward)
    }

    // --- getRecentPaths tests ---

    fun `test getRecentPaths returns empty list for empty history`() {
        val nav = NavigationActions.NavigationHistory()
        assertTrue(nav.getRecentPaths().isEmpty())
    }

    fun `test getRecentPaths returns paths in most recent first order`() {
        val nav = NavigationActions.NavigationHistory()
        nav.push("/first")
        nav.push("/second")
        nav.push("/third")

        val recent = nav.getRecentPaths()
        assertEquals(listOf("/third", "/second", "/first"), recent)
    }

    fun `test getRecentPaths deduplicates paths keeping most recent`() {
        val nav = NavigationActions.NavigationHistory()
        nav.push("/a")
        nav.push("/b")
        nav.push("/a") // duplicate

        val recent = nav.getRecentPaths()
        assertEquals(listOf("/a", "/b"), recent)
    }

    fun `test getRecentPaths caps at 10 entries`() {
        val nav = NavigationActions.NavigationHistory()
        for (i in 1..20) {
            nav.push("/path/$i")
        }

        val recent = nav.getRecentPaths()
        assertEquals(10, recent.size)
        assertEquals("/path/20", recent.first())
    }

    fun `test getRecentPaths only includes paths up to current index`() {
        val nav = NavigationActions.NavigationHistory()
        nav.push("/a")
        nav.push("/b")
        nav.push("/c")
        nav.back() // currentIndex at /b, /c is forward history

        // Should only include paths up to current position, excluding forward history
        val recent = nav.getRecentPaths()
        assertEquals(listOf("/b", "/a"), recent)
        assertFalse(recent.contains("/c"))
    }
}
