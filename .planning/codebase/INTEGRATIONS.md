# External Integrations

**Analysis Date:** 2026-02-28

## APIs & External Services

**JetBrains Marketplace (Publishing):**
- JetBrains Plugin Marketplace - Distribution target for the published plugin `.zip`
  - SDK/Client: `org.jetbrains.intellij.platform` Gradle plugin (Gradle task `publishPlugin`)
  - Auth: `PUBLISH_TOKEN` environment variable (set in GitHub Actions secrets)

**IntelliJ Platform SDK (Internal):**
- IntelliJ Platform IC 2025.1 - The plugin runs inside the IDE and consumes its internal APIs:
  - `com.intellij.openapi.*` - Services, actions, VFS, UI, settings, notifications
  - `com.intellij.ide.*` - App lifecycle, project tree (`TransferableWrapper`, DnD)
  - `com.intellij.openapi.fileEditor.FileDocumentManagerListener` - Save hook for remote file sync (`RemoteEditorSaveListener.kt`)
  - `com.intellij.ide.AppLifecycleListener` - Temp file cleanup on IDE shutdown (`TempFileCleanupListener`)
  - IntelliJ VCS API (`com.intellij.openapi.vcs.*`) - Custom `RemoteGitVcs` integration visible in IDE VCS subsystem

**Terminal Plugin (Bundled IntelliJ):**
- `org.jetbrains.plugins.terminal` - Used by `SshTerminalAction.kt` to open SSH terminal sessions directly inside the IDE's built-in terminal panel

## Data Storage

**Databases:**
- None - No external database used

**Persistent State (IntelliJ Platform PersistentStateComponent):**
All persistent data uses IntelliJ's built-in XML-backed state system (stored in IDE's config directory). Key services:
- `ExplorerSettings` (`src/main/kotlin/ro/faur/explorer/settings/ExplorerSettings.kt`) - Show hidden files, sort folders first, delete behavior
- `BookmarkManager` (`src/main/kotlin/ro/faur/explorer/model/BookmarkManager.kt`) - Local filesystem bookmarks list
- `FrecencyStore` (`src/main/kotlin/ro/faur/explorer/quickopen/ranking/FrecencyStore.kt`) - Visit frequency/recency data for Quick Open ranking
- `TeleportAliasStore` (`src/main/kotlin/ro/faur/explorer/quickopen/aliases/TeleportAliasStore.kt`) - Named directory aliases
- `RemoteExplorerSettings` (`src/main/kotlin/ro/faur/explorer/remote/settings/RemoteExplorerSettings.kt`) - SFTP keepalive and global remote settings
- `RemoteConnectionSettings` (`src/main/kotlin/ro/faur/explorer/remote/settings/RemoteConnectionSettings.kt`) - Per-project saved connection profiles
- `RemoteBookmarkManager` (`src/main/kotlin/ro/faur/explorer/remote/RemoteBookmarkManager.kt`) - Remote SFTP bookmarks
- `RemoteGitSettings` / `RemoteGitVcsManager` (`src/main/kotlin/ro/faur/explorer/remote/git/`) - Remote git configuration per project

**File Storage:**
- Local filesystem (direct Java NIO) - All local file browsing and operations (`FileTreeModel.kt`, `FileActions.kt`, etc.)
- SSH known-hosts file at `~/.ssh/known_hosts_explorer` - Plugin maintains its own known-hosts file separate from the system's; managed by `HostKeyVerifier` (`src/main/kotlin/ro/faur/explorer/remote/security/HostKeyVerifier.kt`)
- Temp files in system temp dir - Used by `NucleoNative` to extract the bundled `.dylib/.so/.dll` on first load; deleted on JVM exit

**Caching:**
- In-memory candidate pool with a 50k file cap - Quick Open file index (`src/main/kotlin/ro/faur/explorer/quickopen/index/CandidatePool.kt`)
- In-memory connection pool keyed by profile name - SFTP sessions in `SftpConnectionManager` (`src/main/kotlin/ro/faur/explorer/remote/SftpConnectionManager.kt`)

## Authentication & Identity

**SSH Authentication:**
- Implementation: Apache MINA SSHD (`SftpConnectionManager.kt`)
- Three auth methods defined in `ConnectionProfile.kt`:
  - `PASSWORD` - Username + password entered via `PasswordPromptDialog.kt`
  - `KEY_FILE` - PEM/OpenSSH private key file path with optional passphrase
  - `AGENT` - Loads default key files (`~/.ssh/id_ed25519`, `id_ecdsa`, `id_rsa`, `id_dsa`) using SSHD's `SecurityUtils`
- Host key verification: TOFU (Trust on First Use) model with UI prompt; stored in `~/.ssh/known_hosts_explorer` (`HostKeyVerifier.kt`)
- Rate limiting on failed connection attempts: `ConnectionRateLimiter.kt` (`src/main/kotlin/ro/faur/explorer/remote/security/`)
- Credential handling: `CredentialHandler.kt` (`src/main/kotlin/ro/faur/explorer/remote/security/CredentialHandler.kt`)

**Plugin Signing (Marketplace):**
- Certificate chain, private key, and passphrase provided via CI env vars at build time (`CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`)
- Signing performed by the IntelliJ Platform Gradle plugin (`signing {}` block in `build.gradle.kts`)

## Monitoring & Observability

**Error Tracking:**
- None - No external error tracking service

**Logs:**
- `com.intellij.openapi.diagnostic.Logger` - IntelliJ's built-in logging API; used throughout the codebase (e.g., `NucleoNative`, `SftpConnectionManager`, `RipgrepContentSearch`)
- `RemoteAuditLogger` (`src/main/kotlin/ro/faur/explorer/remote/security/RemoteAuditLogger.kt`) - Dedicated audit log for remote SFTP operations (security events)
- IDE log viewer (Help > Show Log) - Where all `Logger` output appears at runtime

## CI/CD & Deployment

**Hosting:**
- JetBrains Marketplace - Plugin distribution (`publishPlugin` Gradle task)
- GitHub Actions - All CI and release automation

**CI Pipeline:**
- `ci.yml` (`.github/workflows/ci.yml`) - Triggered on all branch pushes and PRs to `main`:
  - Builds native Rust libraries for 4 platforms in a matrix job (`ubuntu-latest`, `macos-latest`, `windows-latest`)
  - Runs unit tests headlessly
  - Runs light and heavy IntelliJ platform tests using Xvfb
  - Runs UI tests using Remote Robot framework
  - Runs SFTP integration tests with an embedded SSH server
  - Runs plugin verifier against IC 2025.1, 2025.2, 2025.3
  - On `main` push: builds a snapshot `.zip` artifact with build number in version
- `publish.yml` (`.github/workflows/publish.yml`) - Triggered by `v*` tag or manual dispatch:
  - Validates version format and CHANGELOG.md entry
  - Runs full test suite + verifier
  - Requires manual approval (`environment: production`) before releasing
  - Publishes signed plugin to JetBrains Marketplace
  - Creates annotated git tag and GitHub Release
  - Bumps `gradle.properties` to next snapshot version

## Environment Configuration

**Required env vars (runtime):**
- None required at plugin runtime — all plugin configuration is stored via IntelliJ's `PersistentStateComponent` (user-facing settings UI)

**Required env vars (CI/publishing):**
- `PUBLISH_TOKEN` - JetBrains Marketplace API token
- `CERTIFICATE_CHAIN` - Plugin signing certificate chain (PEM)
- `PRIVATE_KEY` - Plugin signing private key
- `PRIVATE_KEY_PASSWORD` - Passphrase for the private key

**Secrets location:**
- GitHub Actions repository secrets (not in source control)

## External CLI Tools (Runtime)

**ripgrep (`rg`):**
- Used by `RipgrepEnumerator.kt` (`src/main/kotlin/ro/faur/explorer/quickopen/backend/RipgrepEnumerator.kt`) for fast file enumeration outside IntelliJ VFS
- Used by `RipgrepContentSearch.kt` (`src/main/kotlin/ro/faur/explorer/quickopen/backend/RipgrepContentSearch.kt`) for in-file content search (`/: query prefix`)
- Detected at well-known paths: `rg`, `/opt/homebrew/bin/rg`, `/usr/local/bin/rg`, `/usr/bin/rg`
- Configurable via Quick Open settings (`QuickOpenSettings.kt`): custom path, hidden files, symlinks, ignore rules, max depth, extra flags
- Falls back gracefully to `VfsEnumerator` if not installed

**git:**
- Used by `LocalGitCommandExecutor.kt` (`src/main/kotlin/ro/faur/explorer/gitpanel/exec/LocalGitCommandExecutor.kt`) via `ProcessBuilder` to run git commands locally
- Used by `RemoteGitCommandExecutor.kt` (`src/main/kotlin/ro/faur/explorer/remote/git/RemoteGitCommandExecutor.kt`) over SSH exec channels for remote git operations

## Webhooks & Callbacks

**Incoming:**
- None - This is a desktop IDE plugin, not a server

**Outgoing:**
- None - All network communication is user-initiated SSH/SFTP to user-configured hosts

---

*Integration audit: 2026-02-28*
