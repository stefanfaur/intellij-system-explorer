# Testing Patterns

**Analysis Date:** 2026-02-28

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) for all unit and SFTP integration tests
- JUnit 3/4 style (`BasePlatformTestCase`) for IntelliJ platform light/heavy tests — bridged via `junit-vintage-engine`
- Config: `build.gradle.kts` — `tasks.test { useJUnitPlatform() }`

**Assertion Library:**
- JUnit 5: `org.junit.jupiter.api.Assertions.*` (`assertEquals`, `assertTrue`, `assertNull`, `assertThrows`, etc.)
- JUnit 3 (light/heavy): inherited `BasePlatformTestCase` assertions (`assertTrue`, `assertEquals`, `assertNotNull`)

**Mocking:**
- `org.mockito:mockito-core:5.14.2`
- `org.mockito.kotlin:mockito-kotlin:5.4.0`

**Coroutine Testing:**
- `kotlinx-coroutines-test:1.10.1` — `runBlocking` used in unit tests to test `Flow`-returning functions

**Run Commands:**
```bash
./gradlew test                                         # Run unit tests only (default, headless-safe)
./gradlew test -PincludeIdeTests=true                  # Include light + heavy IntelliJ platform tests
./gradlew test -PincludeSftpTests=true                 # Include embedded SSH/SFTP integration tests
./gradlew test -PincludeIdeTests=true -PincludeSftpTests=true  # All tests
./gradlew test --tests "ro.faur.explorer.unit.*"       # Specific package
./gradlew test --tests "ro.faur.explorer.sftp.*" -PincludeSftpTests=true  # Specific SFTP tests
```

## Test File Organization

**Location:** All tests in `src/test/kotlin/ro/faur/explorer/`

**Tiers (by directory):**

| Directory | Tier | Style | Requires |
|-----------|------|-------|----------|
| `unit/` | Unit | JUnit 5 | Nothing (pure Kotlin) |
| `unit/remote/` | Unit | JUnit 5 | Nothing (pure Kotlin) |
| `light/` | Light | JUnit 3 (`BasePlatformTestCase`) | IntelliJ platform (headless) |
| `light/remote/` | Light | JUnit 3 (`BasePlatformTestCase`) | IntelliJ platform (headless) |
| `heavy/` | Heavy | JUnit 3 (`BasePlatformTestCase`) | IntelliJ platform + real project |
| `heavy/remote/` | Heavy | JUnit 3 | IntelliJ platform + real project |
| `sftp/` | Integration | JUnit 5 | Embedded SSH server (Apache MINA SSHD) |
| `ui/` | E2E | JUnit 5 + RemoteRobot | Running IDE instance on port 8082 |

**Naming:**
- Unit tests: `{ClassName}Test.kt` or `Phase{N}{DescriptorTest}.kt`
- Light/heavy tests: `{FeatureName}Test.kt`
- SFTP tests: `{Feature}Test.kt` extending `EmbeddedSshTestBase`

**Structure:**
```
src/test/kotlin/ro/faur/explorer/
├── unit/                  # Pure JUnit 5, no platform
│   ├── remote/            # Remote subsystem unit tests
│   └── Phase*.kt          # Feature-phase grouped tests
├── light/                 # BasePlatformTestCase (JUnit 3 style)
│   └── remote/
├── heavy/                 # BasePlatformTestCase, needs real project
│   └── remote/
├── sftp/                  # Embedded SSH server integration tests
│   └── EmbeddedSshTestBase.kt  # Base class providing test SSH server
└── ui/                    # RemoteRobot UI automation (excluded by default)
    └── ExplorerUITestBase.kt   # Base class providing RemoteRobot connection
src/test/testData/         # Static test fixtures (XML, files, SSH configs)
├── bookmarks/
├── filetree/basic/
└── remote/{connections,files,ssh}/
```

## Test Structure

**JUnit 5 Unit Test Suite Organization:**
```kotlin
class Phase1ScoringBehaviorTest {

    private val ranker = FallbackRanker()  // direct instantiation, no mocks

    // Helper factories at bottom
    private fun makePool(vararg names: String): List<SearchCandidate> = names.map { makeCandidate(it) }
    private fun makeCandidate(displayName: String, ...) = SearchCandidate(...)

    @Test
    fun `empty query returns candidates up to limit`() { ... }
}
```

**JUnit 3 Light/Heavy Test Suite Organization:**
```kotlin
class RankerTest : BasePlatformTestCase() {

    private lateinit var ranker: Ranker

    override fun setUp() {
        super.setUp()
        FrecencyStore.getInstance().loadState(FrecencyStore.State())  // reset service state
        ranker = Ranker(FallbackRanker())
    }

    // JUnit 3 naming: "test" prefix or backtick names
    fun `test blank query returns candidates ordered by default`() { ... }
}
```

**Patterns:**
- Setup: `@BeforeEach` (JUnit 5) or `override fun setUp()` calling `super.setUp()` (JUnit 3)
- Teardown: `@AfterEach` for resource cleanup (SSH servers, connection managers)
- Assertions: flat — no nested `describe` blocks
- Test names: backtick strings describing expected behavior: `` `recordVisit increments useCount and updates lastUsedMs` ``

## Mocking

**Framework:** Mockito via `mockito-kotlin`

**What to Mock:**
- IntelliJ platform services that cannot run headlessly in unit tests
- External process dependencies when testing parse logic (test parse functions directly with fixture strings instead)

**What NOT to Mock:**
- Pure Kotlin classes: instantiate directly (e.g., `FrecencyStore()`, `FallbackRanker()`, `ConnectionState()`)
- Settings classes: reset via `loadState(State())` pattern:
  ```kotlin
  override fun setUp() {
      super.setUp()
      ExplorerSettings.getInstance().loadState(ExplorerSettings.State())
  }
  ```
- For SSH/SFTP: use real embedded server (`EmbeddedSshTestBase`) rather than mocking

**Direct Instantiation Pattern (preferred for unit tests):**
```kotlin
// Good — no mocking needed, test real logic
class FrecencyStoreUnitTest {
    private lateinit var store: FrecencyStore

    @BeforeEach
    fun setup() {
        store = FrecencyStore()
        store.loadState(FrecencyStore.State())  // inject clean state
    }
}
```

## Fixtures and Factories

**Test Data:**
```kotlin
// Local factory helpers inside test class (common pattern)
private fun makeCandidate(
    displayName: String,
    type: CandidateType = CandidateType.DIRECTORY,
    fullPath: String = "/root/$displayName"
) = SearchCandidate(
    id = "test:$displayName",
    displayName = displayName,
    fullPath = fullPath,
    parentPath = "/root",
    type = type
)

// Variadic pool builder
private fun makePool(vararg names: String): List<SearchCandidate> =
    names.map { makeCandidate(it) }
```

**Static Fixtures Location:**
- `src/test/testData/bookmarks/` — bookmark XML fixtures
- `src/test/testData/filetree/basic/` — file tree structure fixtures
- `src/test/testData/remote/connections/` — connection profile fixtures
- `src/test/testData/remote/files/` — remote file fixtures
- `src/test/testData/remote/ssh/` — SSH config fixtures

**Embedded Server Fixtures (SFTP tests):**
`EmbeddedSshTestBase` creates temp directories with standard structure on each test run:
```kotlin
// Files created by EmbeddedSshTestBase.createTestFileStructure()
config/app.yml, config/.env, logs/access.log, readme.txt, .hidden/, config-link -> config
```

**Parser tests use inline string fixtures:**
```kotlin
val output = """
    abc123... 1 1 1
    author John Doe
    ...
""".trimIndent()
val result = GitBlameParser.parse(output)
```

## Coverage

**Requirements:** No explicit coverage target enforced in build config

**View Coverage:**
```bash
./gradlew test jacocoTestReport  # if jacoco is added
# Currently no coverage reporting configured
```

## Test Types

**Unit Tests (`unit/`, `unit/remote/`):**
- Scope: Individual class behavior, pure Kotlin, no IntelliJ platform
- Approach: Instantiate class directly, call methods, assert results
- Examples: `FrecencyStoreUnitTest.kt`, `GlobFilterTest.kt`, `ConnectionStateMachineTest.kt`, `GitBlameParserTest.kt`
- Run: Included in default `./gradlew test`

**Light Tests (`light/`, `light/remote/`):**
- Scope: Classes that use IntelliJ services (settings, VFS, project model)
- Approach: Extend `BasePlatformTestCase` — provides headless IntelliJ application
- JUnit style: JUnit 3 method naming (`fun test...` or backtick names starting with `test`)
- Examples: `ExplorerSettingsTest.kt`, `RankerTest.kt`, `BookmarkManagerTest.kt`
- Run: `./gradlew test -PincludeIdeTests=true`

**Heavy Tests (`heavy/`, `heavy/remote/`):**
- Scope: Full IntelliJ project context (VFS writes, clipboard, tool windows)
- Approach: Extend `BasePlatformTestCase`, use `myFixture.tempDirFixture` for VFS operations, `runWriteActionAndWait` for write operations
- Examples: `ClipboardInteropTest.kt`, `ToolWindowLifecycleTest.kt`
- Run: `./gradlew test -PincludeIdeTests=true`

**SFTP Integration Tests (`sftp/`):**
- Scope: Real SSH/SFTP operations against embedded Apache MINA SSHD server
- Approach: Extend `EmbeddedSshTestBase` — spins up server on OS-assigned port (`port = 0`) in `@BeforeEach`, tears down in `@AfterEach`
- Thread drain: `Thread.sleep(500)` in `stopServer()` prevents `ThreadLeakTracker` false positives
- Examples: `SftpConnectionManagerTest.kt`, `SftpFileOperationsTest.kt`, `AuthenticationTest.kt`
- Run: `./gradlew test -PincludeSftpTests=true`

**UI / E2E Tests (`ui/`):**
- Framework: IntelliJ Remote Robot (`com.intellij.remoterobot`)
- Scope: Full IDE automation via robot connecting to `http://127.0.0.1:8082`
- Approach: Extend `ExplorerUITestBase`, IDE launched separately with `-Drobot-server.port=8082` via `testUi` task
- Run: Always excluded from `./gradlew test`; requires `./gradlew runIdeForUiTests testUi` workflow

## Common Patterns

**Coroutine/Flow Testing:**
```kotlin
@Test
fun `search with blank pattern emits no results`() = runBlocking {
    val searcher = RipgrepContentSearch(scope = projectRoot)
    val results = searcher.search("").toList()
    assertTrue(results.isEmpty())
}
```

**Optional Test (skip when tool absent):**
```kotlin
@Test
fun `search returns results for known pattern`() = runBlocking {
    val rg = rgAvailable()
    assumeTrue(rg != null, "rg not installed — skipping content search test")
    // ... test body
}
```

**Error/Exception Testing (JUnit 5):**
```kotlin
@Test
fun `connect from connecting throws IllegalStateException`() {
    val state = ConnectionState()
    state.onConnecting()
    assertThrows<IllegalStateException> { state.onConnecting() }
}
```

**Error/Exception Testing (JUnit 3/4):**
```kotlin
assertThrows(Exception::class.java) {
    manager.connect(profile, "wrongpassword")
}
```

**State Reset Pattern (services):**
```kotlin
override fun setUp() {
    super.setUp()
    ExplorerSettings.getInstance().loadState(ExplorerSettings.State())  // reset to defaults
}
```

**Resource Cleanup with Multiple Managers:**
```kotlin
@AfterEach
fun shutdownManagers() {
    managers.forEach { runCatching { it.shutdown() } }
}
```

**Temp Directory Pattern:**
```kotlin
val tempDir = File(System.getProperty("java.io.tmpdir"), "rg_test_${System.currentTimeMillis()}")
tempDir.mkdirs()
try {
    // ... test using tempDir
} finally {
    tempDir.deleteRecursively()
}
```

---

*Testing analysis: 2026-02-28*
