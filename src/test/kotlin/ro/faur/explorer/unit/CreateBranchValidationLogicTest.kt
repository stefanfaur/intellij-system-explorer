package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ro.faur.explorer.gitpanel.BranchNameValidator

/**
 * Tests the dialog enabling condition without instantiating DialogWrapper.
 *
 * The CreateBranchDialog enables OK when:
 *   BranchNameValidator.validate(text.trim()) == null && text.isNotBlank()
 *
 * This test file verifies that logic directly using BranchNameValidator.
 */
class CreateBranchValidationLogicTest {

    /**
     * Mirrors the condition used by CreateBranchDialog to decide if OK should be enabled.
     * The dialog trims before validating.
     */
    private fun shouldEnableOk(text: String): Boolean {
        val trimmed = text.trim()
        val error = BranchNameValidator.validate(trimmed)
        return error == null && text.isNotBlank()
    }

    // --- Cases that must NOT enable OK ---

    @Test
    fun `empty string does not enable OK`() {
        assertFalse(shouldEnableOk(""))
    }

    @Test
    fun `whitespace-only string does not enable OK`() {
        assertFalse(shouldEnableOk("   "))
    }

    @Test
    fun `single space does not enable OK`() {
        assertFalse(shouldEnableOk(" "))
    }

    @Test
    fun `name with embedded space does not enable OK`() {
        // "invalid name" trims to "invalid name" which contains a space — invalid
        assertFalse(shouldEnableOk("invalid name"))
    }

    @Test
    fun `name starting with dot does not enable OK`() {
        assertFalse(shouldEnableOk(".startswith"))
    }

    @Test
    fun `name ending with dot does not enable OK`() {
        assertFalse(shouldEnableOk("endswith."))
    }

    @Test
    fun `name with double dot does not enable OK`() {
        assertFalse(shouldEnableOk("double..dot"))
    }

    @Test
    fun `name ending with dot lock does not enable OK`() {
        assertFalse(shouldEnableOk("branch.lock"))
    }

    @Test
    fun `name starting with slash does not enable OK`() {
        assertFalse(shouldEnableOk("/startslash"))
    }

    @Test
    fun `name ending with slash does not enable OK`() {
        assertFalse(shouldEnableOk("endslash/"))
    }

    @Test
    fun `name with double slash does not enable OK`() {
        assertFalse(shouldEnableOk("double//slash"))
    }

    @Test
    fun `name with at-brace sequence does not enable OK`() {
        assertFalse(shouldEnableOk("ref@{upstream}"))
    }

    @Test
    fun `lone at sign does not enable OK`() {
        assertFalse(shouldEnableOk("@"))
    }

    @Test
    fun `name with tilde does not enable OK`() {
        assertFalse(shouldEnableOk("tilde~1"))
    }

    @Test
    fun `name with caret does not enable OK`() {
        assertFalse(shouldEnableOk("caret^0"))
    }

    // --- Cases that SHOULD enable OK ---

    @Test
    fun `simple valid name enables OK`() {
        assertTrue(shouldEnableOk("valid"))
    }

    @Test
    fun `slash-separated path enables OK`() {
        assertTrue(shouldEnableOk("feature/branch"))
    }

    @Test
    fun `hyphenated name enables OK`() {
        assertTrue(shouldEnableOk("my-feature-branch"))
    }

    @Test
    fun `name with digits enables OK`() {
        assertTrue(shouldEnableOk("feat-123"))
    }

    @Test
    fun `version-style name with single dots enables OK`() {
        assertTrue(shouldEnableOk("v1.0.0"))
    }

    @Test
    fun `main branch name enables OK`() {
        assertTrue(shouldEnableOk("main"))
    }

    @Test
    fun `develop branch name enables OK`() {
        assertTrue(shouldEnableOk("develop"))
    }

    // --- Trim interaction ---

    @Test
    fun `text with surrounding whitespace around valid name enables OK after trim`() {
        // The dialog trims before validating. "  valid  ".trim() = "valid" which is valid.
        // But text.isNotBlank() = true, so OK should be enabled.
        assertTrue(shouldEnableOk("  valid  "))
    }

    @Test
    fun `text with surrounding whitespace around invalid name does not enable OK`() {
        // "  .bad  ".trim() = ".bad" which starts with '.' — invalid.
        assertFalse(shouldEnableOk("  .bad  "))
    }

    @Test
    fun `trimmed feature-slash-name enables OK`() {
        assertTrue(shouldEnableOk("  feature/login  "))
    }
}
