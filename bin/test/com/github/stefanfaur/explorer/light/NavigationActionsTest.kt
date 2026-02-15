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
}
