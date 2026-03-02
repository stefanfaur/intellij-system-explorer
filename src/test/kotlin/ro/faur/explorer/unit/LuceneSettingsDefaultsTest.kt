package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.settings.QuickOpenSettings

/**
 * Automated coverage for human verification item 1:
 * "Open QuickOpen Settings and confirm 'Lucene Index' group with 4 controls is visible."
 *
 * The visual rendering requires a running IDE, but the field defaults (which drive the controls)
 * are fully testable without platform bootstrap.
 */
class LuceneSettingsDefaultsTest {

    @Test
    fun `luceneHybridThreshold defaults to 5000`() {
        val state = QuickOpenSettings.State()
        assertEquals(5_000, state.luceneHybridThreshold,
            "Hybrid threshold should be 5000 files — below this the plugin uses live VFS enumeration")
    }

    @Test
    fun `luceneMaxIndexSizeMb defaults to 500 and luceneEvictionDays to 30`() {
        val state = QuickOpenSettings.State()
        assertEquals(500, state.luceneMaxIndexSizeMb,
            "Default max index content size should be 500 MB")
        assertEquals(30, state.luceneEvictionDays,
            "Default eviction window should be 30 days")
    }

    @Test
    fun `luceneExtensionAllowlist contains all expected code and text extensions`() {
        val state = QuickOpenSettings.State()
        val exts = state.luceneExtensionAllowlist
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        // Core code extensions
        assertAll(
            { assertTrue("kt" in exts, "Kotlin extension missing from allowlist") },
            { assertTrue("java" in exts, "Java extension missing from allowlist") },
            { assertTrue("py" in exts, "Python extension missing from allowlist") },
            { assertTrue("ts" in exts, "TypeScript extension missing from allowlist") },
            { assertTrue("js" in exts, "JavaScript extension missing from allowlist") },
            { assertTrue("go" in exts, "Go extension missing from allowlist") },
        )
        // Config / text extensions
        assertAll(
            { assertTrue("md" in exts, "Markdown extension missing from allowlist") },
            { assertTrue("json" in exts, "JSON extension missing from allowlist") },
            { assertTrue("yaml" in exts, "YAML extension missing from allowlist") },
            { assertTrue("yml" in exts, "YML extension missing from allowlist") },
            { assertTrue("xml" in exts, "XML extension missing from allowlist") },
        )
        assertTrue(exts.size >= 10, "Allowlist should contain at least 10 extensions, got ${exts.size}")
    }
}
