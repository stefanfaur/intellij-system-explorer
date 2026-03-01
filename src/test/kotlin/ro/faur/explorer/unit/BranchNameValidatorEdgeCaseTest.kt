package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ro.faur.explorer.gitpanel.BranchNameValidator

class BranchNameValidatorEdgeCaseTest {

    // --- Whitespace variants ---

    @Test
    fun `tab character is invalid`() {
        assertNotNull(BranchNameValidator.validate("branch\tname"))
    }

    @Test
    fun `newline character is invalid`() {
        assertNotNull(BranchNameValidator.validate("branch\nname"))
    }

    @Test
    fun `carriage return character is invalid`() {
        assertNotNull(BranchNameValidator.validate("branch\rname"))
    }

    @Test
    fun `string with only tab is invalid`() {
        assertNotNull(BranchNameValidator.validate("\t"))
    }

    @Test
    fun `string with only newline is invalid`() {
        assertNotNull(BranchNameValidator.validate("\n"))
    }

    // --- Boundary conditions for dot rules ---

    @Test
    fun `single dot alone is invalid`() {
        // "." starts with dot AND ends with dot
        assertNotNull(BranchNameValidator.validate("."))
    }

    @Test
    fun `double dot alone is invalid`() {
        assertNotNull(BranchNameValidator.validate(".."))
    }

    @Test
    fun `name containing dot-lock in the middle but not ending is tested`() {
        // "feature.lockdown" does NOT end with ".lock" — should be valid per spec
        assertNull(BranchNameValidator.validate("feature.lockdown"))
    }

    @Test
    fun `name ending with dot-lock-suffix is invalid`() {
        // "my-branch.lock" ends with ".lock" — must be invalid
        assertNotNull(BranchNameValidator.validate("my-branch.lock"))
    }

    @Test
    fun `name ending with LOCK uppercase is valid`() {
        // spec says ".lock" check is case-sensitive; ".LOCK" is NOT rejected
        assertNull(BranchNameValidator.validate("branch.LOCK"))
    }

    @Test
    fun `name with dot-lock embedded and additional suffix is valid`() {
        // "some.lockfile" does not end with ".lock"
        assertNull(BranchNameValidator.validate("some.lockfile"))
    }

    // --- Multi-level slash paths ---

    @Test
    fun `multiple valid slashes are valid`() {
        assertNull(BranchNameValidator.validate("feature/task/subtask"))
    }

    @Test
    fun `refs-heads-main is valid`() {
        // slashes are allowed; no rule about refs/ prefix
        assertNull(BranchNameValidator.validate("refs/heads/main"))
    }

    // --- @{ sequence variants ---

    @Test
    fun `at-brace at start of name is invalid`() {
        assertNotNull(BranchNameValidator.validate("@{upstream}"))
    }

    @Test
    fun `at-brace in middle of name is invalid`() {
        assertNotNull(BranchNameValidator.validate("branch@{0}"))
    }

    // --- Lone @ only exactly ---

    @Test
    fun `at-sign embedded in longer name is valid`() {
        // only the lone "@" is forbidden; "user@domain" has no @{ sequence
        assertNull(BranchNameValidator.validate("user@domain"))
    }

    // --- Unicode characters ---

    @Test
    fun `unicode letters in name do not crash and return a result`() {
        // spec has no explicit rule; just assert it doesn't throw
        val result = BranchNameValidator.validate("brânch")
        // We make no assertion about valid/invalid beyond no exception
        // but if the implementation rejects it, result is non-null; if accepts, null
        // This test primarily guards against crashes
        assert(result == null || result.isNotBlank()) {
            "validate must return null or a non-blank error string, got: $result"
        }
    }

    // --- Long names ---

    @Test
    fun `very long name with 255 valid chars does not crash`() {
        val longName = "a".repeat(255)
        val result = BranchNameValidator.validate(longName)
        // No spec rule about max length; assert it doesn't throw
        assert(result == null || result.isNotBlank()) {
            "validate must return null or a non-blank error string, got: $result"
        }
    }

    // --- Reserved words (no explicit spec rule) ---

    @Test
    fun `HEAD is not rejected by validator`() {
        // No spec rule about reserved words; "HEAD" should be structurally valid
        assertNull(BranchNameValidator.validate("HEAD"))
    }

    @Test
    fun `FETCH_HEAD is not rejected by validator`() {
        assertNull(BranchNameValidator.validate("FETCH_HEAD"))
    }

    // --- Leading/trailing slash boundary combinations ---

    @Test
    fun `double slash at start is invalid`() {
        assertNotNull(BranchNameValidator.validate("//starts-double-slash"))
    }

    @Test
    fun `double slash at end is invalid`() {
        assertNotNull(BranchNameValidator.validate("ends-double-slash//"))
    }

    // --- Combination rules ---

    @Test
    fun `name with dot then dot-lock ending is invalid`() {
        // "v1.0.lock" ends with ".lock"
        assertNotNull(BranchNameValidator.validate("v1.0.lock"))
    }

    @Test
    fun `name starting with dot and also containing double dot is invalid`() {
        assertNotNull(BranchNameValidator.validate("..hidden"))
    }

    @Test
    fun `name with space and valid prefix is invalid`() {
        // Even if prefix would be valid, space makes the whole thing invalid
        assertNotNull(BranchNameValidator.validate("feature/ branch"))
    }

    @Test
    fun `empty string returns error mentioning empty`() {
        val error = BranchNameValidator.validate("")
        assertNotNull(error)
        // Spec says the error for empty is "Branch name cannot be empty"
        assert(error!!.contains("empty", ignoreCase = true)) {
            "Expected error message to mention 'empty', but got: $error"
        }
    }

    @Test
    fun `blank string returns non-null error`() {
        // "   " is blank but not empty; spec says blank → error "Branch name cannot be empty"
        val error = BranchNameValidator.validate("   ")
        assertNotNull(error)
    }

    // --- Trailing dot combined with lock ---

    @Test
    fun `name that is exactly dot-lock is invalid`() {
        // ".lock" starts with dot AND ends with .lock — doubly invalid
        assertNotNull(BranchNameValidator.validate(".lock"))
    }

    // --- Control characters ---

    @Test
    fun `null character is invalid`() {
        assertNotNull(BranchNameValidator.validate("branch\u0000name"))
    }

    @Test
    fun `del control character is invalid`() {
        assertNotNull(BranchNameValidator.validate("branch\u007fname"))
    }
}
