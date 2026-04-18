`# Repository Guidelines

## Project Structure & Module Organization
Core plugin code lives in `src/main/kotlin/ro/faur/explorer/` and is organized by concern: `actions/`, `model/`, `settings/`, `ui/`, and `util/`. Plugin metadata and icons are in `src/main/resources/META-INF/` (`plugin.xml`, `pluginIcon*.svg`).

Tests live in `src/test/kotlin/ro/faur/explorer/` and are split by scope:
- `unit/` for pure logic tests
- `light/` and `heavy/` for IntelliJ platform tests
- `ui/` for RemoteRobot end-to-end UI tests

Automation and release helpers are in `.github/workflows/` and `.github/scripts/`.

## Build, Test, and Development Commands
- `./gradlew runIde` launches a sandbox IDE with the plugin loaded.
- `./gradlew buildPlugin` builds the distributable ZIP in `build/distributions/`.
- `./gradlew test` runs JUnit-platform tests (UI tests excluded).
- `./gradlew testIdeUi --tests "ro.faur.explorer.light.*"` runs light platform tests.
- `./gradlew testIdeUi --tests "ro.faur.explorer.heavy.*"` runs heavy platform tests.
- `./gradlew testUi` runs UI integration tests.
- `./gradlew verifyPlugin runPluginVerifier` checks IntelliJ compatibility.

## Coding Style & Naming Conventions
Use Kotlin (JDK 21) with 4-space indentation and standard IntelliJ formatting. Keep packages under `ro.faur.explorer.<area>`. Use `UpperCamelCase` for classes and `lowerCamelCase` for functions/properties.

Test files should end with `*Test.kt`, and test methods commonly use backticked descriptive names (example: ``fun `test create new file`()``). Prefer VFS-safe operations and IntelliJ write actions for filesystem changes.

## Testing Guidelines
Add or update tests for every behavior change:
- Logic-only behavior: `unit/`
- IDE-integrated behavior: `light/` or `heavy/`
- End-to-end tool window interactions: `ui/`

Run focused tests while iterating, then run `./gradlew test` before opening a PR.

## Commit & Pull Request Guidelines
Follow the repository’s Conventional Commit style seen in history: `fix: ...`, `docs: ...`, `ci: ...`, `chore: ...`, `perf: ...`.

PRs should include:
- A clear summary of behavior changes
- Linked issue(s) when applicable
- Test evidence (commands run and results)
- Screenshots/GIFs for UI-visible changes

For release-related changes, ensure `CHANGELOG.md` has the target version entry format (`## [X.Y.Z] - YYYY-MM-DD`).
