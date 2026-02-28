# Coding Conventions

**Analysis Date:** 2026-02-28

## Naming Patterns

**Files:**
- PascalCase for all Kotlin source files matching their class name: `FrecencyStore.kt`, `PathUtils.kt`, `RipgrepContentSearch.kt`
- Test files named after the class under test with `Test` suffix: `FileSizeFormatterTest.kt`, `FrecencyStoreUnitTest.kt`
- Phase-prefixed tests for feature-grouped tests: `Phase1ScoringBehaviorTest.kt`, `Phase6RipgrepContentSearchTest.kt`

**Classes and Interfaces:**
- PascalCase: `FrecencyStore`, `SearchCandidate`, `GitBackend`, `ContentMatch`
- Interface names are conceptual, not prefixed with `I`: `GitBackend`, `RankerBackend`, `BrowserPanel`
- Singleton objects use PascalCase: `PathUtils`, `FileSizeFormatter`

**Functions:**
- camelCase: `recordVisit`, `getSignals`, `navigateTo`, `breadcrumbs`
- Private helpers use short, descriptive names: `evictIfNeeded`, `refreshNavButtons`, `bindNavCallbackToPanel`
- Boolean functions use `is`/`can`/`has` prefix: `isAvailable()`, `canGoBack()`, `isHidden()`, `isConnected()`

**Variables:**
- camelCase: `serverRoot`, `halfLife`, `testRoot`
- Constants in `companion object` use SCREAMING_SNAKE_CASE: `MAX_ENTRIES`, `CONNECT_TIMEOUT_MS`, `DEFAULT_MAX_CONTENT_RESULTS`
- Private backing fields use `my` prefix for IntelliJ platform state: `myState` (follows IntelliJ SDK convention)

**Types:**
- Data classes for value objects: `SearchCandidate`, `ContentMatch`, `CommitInfo`, `Breadcrumb`
- Enums in PascalCase with SCREAMING_SNAKE_CASE values: `BackendType { LOCAL, REMOTE }`, `ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, DEGRADED }`

## Code Style

**Formatting:**
- No explicit formatter config file (`.editorconfig`, `.prettierrc`) detected — follows IntelliJ defaults
- 4-space indentation (Kotlin standard)
- Trailing comma on multi-line argument lists is used: `listOf("*.so", "*.dylib", "*.dll")`

**Linting:**
- No `.eslintrc` equivalent detected; relies on Kotlin compiler warnings

**Kotlin Idioms:**
- Prefer `apply` block for object configuration:
  ```kotlin
  JButton(icon).apply {
      isBorderPainted = false
      isContentAreaFilled = false
      toolTipText = tooltip
  }
  ```
- Use `also` for side-effectful chaining:
  ```kotlin
  SftpConnectionManager().also { it.connect(profile, password, keyPassphrase) }
  ```
- `getOrPut` for map initialization: `myState.entries.getOrPut(path) { Entry() }`
- Elvis operator for null defaults: `parent?.toString() ?: "/"`
- `runCatching` for swallowing exceptions in tests: `runCatching { it.shutdown() }`
- Blank catch with `_` name to suppress unused exception variable: `catch (_: Exception) { null }`

## Import Organization

**Order:**
1. `com.intellij.*` platform imports
2. `kotlinx.*` coroutines/stdlib
3. `java.*` / `javax.*` standard library
4. `ro.faur.explorer.*` internal project imports

**Path Aliases:**
- No path aliases; full package names used throughout

## Error Handling

**Patterns:**
- Platform-integrated code uses `Logger.getInstance(ClassName::class.java)` for logging errors; example in `RipgrepContentSearch`:
  ```kotlin
  LOG.warn("Content search error for pattern=$pattern scope=$scope", e)
  ```
- Swallow-and-return-null for non-critical paths: `catch (_: Exception) { null }`
- Swallow-and-return-default for settings reads: `catch (_: Exception) { 24.0 }`
- Propagate exceptions from critical connection code: `SftpConnectionManager.connect()` throws on failure
- `finally` blocks used to clean up resources (processes, executors): `process.destroy()`, `executor.shutdown()`
- UI error feedback via `NotificationGroupManager` (not dialog popups) for background failures

## Logging

**Framework:** IntelliJ platform `Logger` (`com.intellij.openapi.diagnostic.Logger`)

**Pattern:**
```kotlin
companion object {
    private val LOG = Logger.getInstance(RipgrepContentSearch::class.java)
}
// Usage:
LOG.warn("Content search error for pattern=$pattern scope=$scope", e)
```

**What to log:**
- `LOG.warn` for recoverable errors with context (pattern, scope)
- `LOG.info` for significant lifecycle events
- Do NOT log in tight loops or hot paths

## Comments

**When to Comment:**
- KDoc (`/** */`) on classes that need threading/lifecycle notes:
  ```kotlin
  /**
   * Application-scoped persistent store for frecency usage signals.
   * Persists to explorerFrecency.xml and survives IDE restart.
   * Thread-safe for reads; writes must be on EDT or synchronized.
   */
  ```
- Inline section headers with `// ── Name ────` for grouping in large files
- Single-line `/** ... */` on public methods with non-obvious behavior

**Test Comments:**
- Phase-level tests include a header comment explaining what behaviors are tested and the phase context

## Function Design

**Size:** Functions are kept small and focused; long functions (e.g., `connectToRemote` in `ExplorerPanel.kt`) use nested local functions (`fun onSuccess`, `fun onError`, `fun onCancelled`) to decompose logic

**Parameters:** Prefer explicit named parameters for boolean flags; default parameter values used extensively:
```kotlin
fun recordVisit(path: String, branch: String? = null)
fun getLog(maxCount: Int = 100): List<GitLogEntry>
```

**Return Values:** Use nullable return types rather than sentinel values; `null` signals absence

## Module Design

**Exports:**
- `companion object { fun getInstance() }` pattern for IntelliJ application/project services:
  ```kotlin
  companion object {
      fun getInstance(): FrecencyStore =
          ApplicationManager.getApplication().getService(FrecencyStore::class.java)
  }
  ```
- `object` singletons for stateless utilities: `PathUtils`, `FileSizeFormatter`, `GitBlameParser`

**Barrel Files:** Not used; imports reference specific files directly

## IntelliJ Platform Patterns

**Services:**
- Application-scoped: extend `PersistentStateComponent<T>`, annotate with `@State` + `@Storage`
- Inner `State` data class holds all persisted fields with defaults
- `loadState`/`getState` pair implements serialization

**Thread Safety:**
- `@Synchronized` annotation on methods that mutate shared state (`recordVisit`, `getSignals`, `setBookmarked`)
- UI updates always via `SwingUtilities.invokeLater` or `invokeAndWait`
- Background work on `Executors.newSingleThreadExecutor()`

---

*Convention analysis: 2026-02-28*
