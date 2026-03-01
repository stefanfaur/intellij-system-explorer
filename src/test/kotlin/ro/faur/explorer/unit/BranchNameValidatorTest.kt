package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ro.faur.explorer.gitpanel.BranchNameValidator

class BranchNameValidatorTest {

    // --- Valid names (expect null = no error) ---

    @Test
    fun `validate returns null for simple valid name`() {
        assertNull(BranchNameValidator.validate("main"))
    }

    @Test
    fun `validate returns null for slash-separated path`() {
        assertNull(BranchNameValidator.validate("feature/my-branch"))
    }

    @Test
    fun `validate returns null for hyphen-and-digits name`() {
        assertNull(BranchNameValidator.validate("feat-123"))
    }

    @Test
    fun `validate returns null for valid-branch-name`() {
        assertNull(BranchNameValidator.validate("valid-branch-name"))
    }

    @Test
    fun `validate returns null for version tag with single dot`() {
        assertNull(BranchNameValidator.validate("v1.0.0"))
    }

    // --- Invalid names (expect non-null error string) ---

    @Test
    fun `validate returns error for empty string`() {
        assertNotNull(BranchNameValidator.validate(""))
    }

    @Test
    fun `validate returns error for blank string`() {
        assertNotNull(BranchNameValidator.validate("  "))
    }

    @Test
    fun `validate returns error for name with space`() {
        assertNotNull(BranchNameValidator.validate("has space"))
    }

    @Test
    fun `validate returns error for double dot`() {
        assertNotNull(BranchNameValidator.validate("double..dot"))
    }

    @Test
    fun `validate returns error for tilde`() {
        assertNotNull(BranchNameValidator.validate("tilde~1"))
    }

    @Test
    fun `validate returns error for caret`() {
        assertNotNull(BranchNameValidator.validate("caret^0"))
    }

    @Test
    fun `validate returns error for colon`() {
        assertNotNull(BranchNameValidator.validate("colon:name"))
    }

    @Test
    fun `validate returns error for question mark`() {
        assertNotNull(BranchNameValidator.validate("question?mark"))
    }

    @Test
    fun `validate returns error for asterisk`() {
        assertNotNull(BranchNameValidator.validate("asterisk*glob"))
    }

    @Test
    fun `validate returns error for open bracket`() {
        assertNotNull(BranchNameValidator.validate("bracket[0]"))
    }

    @Test
    fun `validate returns error for backslash`() {
        assertNotNull(BranchNameValidator.validate("back\\slash"))
    }

    @Test
    fun `validate returns error for at-brace sequence`() {
        assertNotNull(BranchNameValidator.validate("at@{upstream}"))
    }

    @Test
    fun `validate returns error for lone at sign`() {
        assertNotNull(BranchNameValidator.validate("@"))
    }

    @Test
    fun `validate returns error when starting with dot`() {
        assertNotNull(BranchNameValidator.validate(".startswith"))
    }

    @Test
    fun `validate returns error when ending with dot`() {
        assertNotNull(BranchNameValidator.validate("endswith."))
    }

    @Test
    fun `validate returns error when starting with slash`() {
        assertNotNull(BranchNameValidator.validate("/startslash"))
    }

    @Test
    fun `validate returns error when ending with slash`() {
        assertNotNull(BranchNameValidator.validate("endslash/"))
    }

    @Test
    fun `validate returns error for double slash`() {
        assertNotNull(BranchNameValidator.validate("double//slash"))
    }

    @Test
    fun `validate returns error for name ending with dot lock`() {
        assertNotNull(BranchNameValidator.validate("ends.lock"))
    }
}
