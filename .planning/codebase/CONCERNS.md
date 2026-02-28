# Codebase Concerns

**Analysis Date:** 2026-02-28

## Tech Debt

**NucleoNative JNI libraries not bundled in plugin JAR:**
- Issue: `NucleoNative.kt` expects native libraries at `/natives/<platform>/libfuzzyjni.{dylib,so,dll}` inside the plugin JAR, but `src/main/resources/` only contains `META-INF/` files. The compiled `.dylib` files exist only under `rust-fuzzy/target/` (build artifacts). Without a Gradle task copying them into `src/main/resources/natives/`, every user gets silent fallback to `MinusculeMatcherRanker`.
- Files: `src/main/kotlin/ro/faur/explorer/quickopen/backend/NucleoNative.kt`, `src/main/kotlin/ro/faur/explorer/quickopen/backend/RankerSelector.kt`
- Impact: The Nucleo fuzzy ranker (the best scorer) is silently unavailable. Users never know — the settings page shows "nucleo unavailable, using fallback" but the fallback quality is lower.
- Fix approach: Add a Gradle `copyNatives` task that copies `rust-fuzzy/target/release/libfuzzyjni.*` into `src/main/resources/natives/<platform>/` before `processResources`. Gate on platform detection.

**QuickOpenPanel uses ad-hoc CoroutineScope with no parent:**
- Issue: `performContentSearch()` creates `CoroutineScope(Dispatchers.IO + Job())` inline, meaning there is no parent scope to cancel it if the panel is disposed mid-search. The launched job is then blocked on a `CountDownLatch` with a 10-second timeout — mixing coroutines and old-style blocking synchronization.
- Files: `src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt` line 515
- Impact: Potential goroutine leak per content search if the popup is dismissed before the latch resolves. The 10-second block holds a thread from `Dispatchers.IO`.
- Fix approach: Reuse the existing `searchScheduler` or pass in a `CoroutineScope` tied to the panel's `dispose()`. Replace the `CountDownLatch` with `coroutineScope {}` + `runBlocking {}` idiom, or switch to fully suspending collection.

**`SftpFileTreeModel` and `RemoteBrowserPanel` use raw `Thread { }` for I/O:**
- Issue: Both files spawn daemon threads inline for every directory listing and lazy expansion. There is no upper bound on concurrently spawned threads (rapid navigation or many simultaneous expansions creates many threads).
- Files: `src/main/kotlin/ro/faur/explorer/remote/SftpFileTreeModel.kt` lines 61, 109; `src/main/kotlin/ro/faur/explorer/remote/ui/RemoteBrowserPanel.kt` lines 240, 400, 420, 440, 461, 542
- Impact: Under heavy use or slow connections, many threads accumulate. No cancellation — if the user navigates away while a listing is loading, the thread finishes and then dispatches an EDT update that may operate on stale state.
- Fix approach: Switch to `AppExecutorUtil.getAppExecutorService().submit(...)` or a bounded coroutine scope so IntelliJ's executor pool manages the threads.

**`RemoteGitStatusCache` has unbounded size and O(n) lookup:**
- Issue: The cache is a flat `ConcurrentHashMap` with no size limit or eviction (the `TODO` at line 13 notes this). `getStatus()` iterates all keys looking for a prefix match — O(n) per lookup where n is the number of cached repo paths.
- Files: `src/main/kotlin/ro/faur/explorer/remote/git/RemoteGitStatusCache.kt`
- Impact: In large-repo environments the cache grows without bound. Each status lookup for every tree cell render scans all cached keys.
- Fix approach: Implement the `disableOnRepoSizeFiles` guard noted in the TODO. Add LRU eviction using the same `DirectoryCache` pattern (access-order `LinkedHashMap`).

**`LocalGitCommandExecutor` reads stdout/stderr on ad-hoc daemon threads:**
- Issue: Two raw daemon threads are started (`stdoutThread`, `stderrThread`) per git command execution without any pool or lifecycle management.
- Files: `src/main/kotlin/ro/faur/explorer/gitpanel/exec/LocalGitCommandExecutor.kt` lines 22–23
- Impact: Minor thread proliferation on frequent git operations (log, status polling).
- Fix approach: Use `process.inputStream.readBytes()` in a single thread after `process.waitFor()`, or redirect via `ProcessBuilder.redirectOutput()`.

**`RemoteGitCommandExecutor.executeBlocking` duplicated in `executeBytesBlocking`:**
- Issue: The SSH channel setup, command construction (`safeDirFlag`, `GIT_PAGER=cat`, `GIT_TERMINAL_PROMPT=0`), and teardown are copy-pasted verbatim between `executeBlocking()` and `executeBytesBlocking()`.
- Files: `src/main/kotlin/ro/faur/explorer/remote/git/RemoteGitCommandExecutor.kt`
- Impact: Bug fixes or improvements to one method must be manually replicated in the other. Already caused a divergence: `executeBytesBlocking` does not log failures.
- Fix approach: Extract a private `executeRaw(command: String, timeout: Duration): Pair<ByteArray, ByteArray>` method that both callers use.

**`QuickOpenPanel.popup` is `lateinit var` set externally after construction:**
- Issue: `popup` is marked `lateinit var` and is set by `QuickOpenPopup` after the panel is constructed. Any code path that calls `popup.closeOk(null)` before `popup` is set (e.g. during construction's `scheduleSearch`) would throw `UninitializedPropertyAccessException`.
- Files: `src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt` line 149
- Impact: Potential crash on very fast keystroke during popup initialization, though currently unlikely because search is debounced.
- Fix approach: Accept the popup as a constructor parameter, or use `var popup: JBPopup? = null` with null-safe calls.

## Known Bugs

**`RemoteGitBackend.getLog` silently returns empty list when git is unavailable:**
- Symptoms: The git log panel appears empty even when the remote repo is valid but `git` is not on the remote server's PATH (exit code 127). This is identical to a legitimate empty repository.
- Files: `src/main/kotlin/ro/faur/explorer/gitpanel/RemoteGitBackend.kt` lines 30–38, `src/test/kotlin/ro/faur/explorer/sftp/RemoteGitBackendSshTest.kt` lines 182–200
- Trigger: Connect to a remote server where `git` is not installed or not on PATH.
- Workaround: Check the IDE diagnostic log — `RemoteGitCommandExecutor` does emit a `LOG.warn` on failures, but the user has no UI feedback.

**`FileTreeComponent` folder item count calls `vf.children.size` on every render:**
- Symptoms: When "Show item count in folders" is enabled, `vf.children.size` is called inside `customizeCellRenderer` for every visible directory node on every repaint. This can trigger VFS reads on the EDT.
- Files: `src/main/kotlin/ro/faur/explorer/ui/FileTreeComponent.kt` line 628
- Trigger: Enable "Show item count in folders" in settings with a directory containing many subdirectories.
- Workaround: Disable the option. The test `CellRendererPerformanceTest.kt` documents this as a known issue.

## Security Considerations

**`ripgrepExtraFlags` allows arbitrary flag injection into `rg` invocations:**
- Risk: The user-configurable `ripgrepExtraFlags` setting is split on whitespace and appended directly to the `ProcessBuilder` command list for both `RipgrepEnumerator` and `RipgrepContentSearch`. A user could pass flags like `--exec` (which rg does not have, but future rg versions or obscure flags could have side effects). More critically, if a setting is imported or synced from an untrusted source, arbitrary rg flags execute in the user's context.
- Files: `src/main/kotlin/ro/faur/explorer/quickopen/backend/RipgrepEnumerator.kt` line 49, `src/main/kotlin/ro/faur/explorer/quickopen/backend/RipgrepContentSearch.kt` line 77
- Current mitigation: `ProcessBuilder` with a list (not shell string) prevents shell injection. The risk is rg-specific flags, not arbitrary commands.
- Recommendations: Validate `extraFlags` against an allowlist of safe rg flags (e.g. `--hidden`, `--no-ignore`, `--follow`, `--type`, `--glob`). Reject flags starting with `--exec`.

**`SftpConnectionManager.connect` with `AGENT` auth mode uses `FilePasswordProvider.EMPTY`:**
- Risk: When loading key files for agent-fallback authentication, `FilePasswordProvider.EMPTY` is passed. Passphrase-protected keys will silently fail to load rather than prompting the user.
- Files: `src/main/kotlin/ro/faur/explorer/remote/SftpConnectionManager.kt` lines 124–136
- Current mitigation: Keys without passphrases work correctly.
- Recommendations: Use the same `keyPassphrase` prompt flow as `KEY_FILE` auth, or surface a clear error when a passphrase-protected key is detected in agent mode.

**Temp files for remote editing stored in `~/.cache/system-explorer/temp`:**
- Risk: Downloaded remote file contents are cached to disk at a predictable path derived from the host and remote path. On shared machines, other users with filesystem access could read sensitive remote files. The secure-delete option overwrites with zeros but uses a predictable pattern.
- Files: `src/main/kotlin/ro/faur/explorer/remote/security/SecureTempFileManager.kt`
- Current mitigation: On Unix, directories and files are created with `700`/`600` permissions. `cleanupAll()` is called on disconnect and IDE close via `TempFileCleanupListener`.
- Recommendations: Use `Files.createTempDirectory` with a random suffix rather than a predictable path, so temp files disappear on system reboot. The current `~/.cache` location survives reboots.

## Performance Bottlenecks

**`RemoteGitStatusCache.getStatus` performs linear key scan:**
- Problem: `getStatus` iterates all cache entries to find keys matching a connection prefix. With many open connections or repos, this is O(cache_size) per call.
- Files: `src/main/kotlin/ro/faur/explorer/remote/git/RemoteGitStatusCache.kt` lines 24–37
- Cause: The flat `ConcurrentHashMap` keyed by encoded strings does not support prefix queries efficiently.
- Improvement path: Index the cache by connection name at the outer level: `ConcurrentHashMap<String, ConcurrentHashMap<String, Map<String, GitFileStatus>>>`.

**`showFolderItemCount` triggers VFS access on every cell render:**
- Problem: `vf.children.size` in `FileTreeComponent.VirtualFileCellRenderer` is called on every paint for every visible directory node. The VFS `children` property may trigger disk access if the directory's children are not yet loaded.
- Files: `src/main/kotlin/ro/faur/explorer/ui/FileTreeComponent.kt` line 628
- Cause: No caching of the child count; the count is re-queried on each render.
- Improvement path: Cache child counts in `FileTreeComponent.permissionsCache` or a dedicated `childCountCache` populated during `setRoot` / `treeWillExpand`.

**`FileTreeComponent.truncateString` uses binary search with `fm.stringWidth` per iteration:**
- Problem: The binary search in `VirtualFileCellRenderer.truncateString` calls `fm.stringWidth` up to O(log n) times per cell render when permissions are enabled. `FontMetrics.stringWidth` is not free.
- Files: `src/main/kotlin/ro/faur/explorer/ui/FileTreeComponent.kt` lines 641–672
- Cause: Generic binary search approach instead of iterating characters and accumulating widths once.
- Improvement path: Accumulate character widths in a single pass; stop when the sum exceeds `availableWidth`.

## Fragile Areas

**DnD dual-system architecture requires careful maintenance:**
- Files: `src/main/kotlin/ro/faur/explorer/actions/DragDropHandler.kt`, `src/main/kotlin/ro/faur/explorer/actions/FileTreeTransferHandler.kt`, `src/main/kotlin/ro/faur/explorer/ui/FileTreeComponent.kt`
- Why fragile: The local tree uses Swing `TransferHandler` + `dragEnabled=true` for drag-out, and IntelliJ `DnDNativeTarget` for drop-in. Adding any additional DnD registration (e.g. `DnDSource`) will conflict with Swing's `DragGestureRecognizer` and break drag initiation. `TransferableWrapper` does not extend `Transferable` — using Transferable flavor APIs on it silently returns nothing.
- Safe modification: Any new DnD functionality must stay within the existing Swing TransferHandler for drag-out, and DnDNativeTarget for drop-in. Never register as `DnDSource` on the tree. See MEMORY.md for the full architectural constraint.
- Test coverage: `src/test/kotlin/ro/faur/explorer/light/DragDropHandlerTest.kt` covers basic scenarios but does not test the Swing drag-out path.

**`SftpConnectionManager.getSftpClient` has double-checked locking on a data class field:**
- Files: `src/main/kotlin/ro/faur/explorer/remote/SftpConnectionManager.kt` lines 179–187
- Why fragile: `ConnectionEntry` is a `data class` with a `@Volatile var sftpClient`. The `synchronized(entry)` block checks `entry.sftpClient == null || !entry.sftpClient!!.isOpen` — the `!!` on a `@Volatile` field is safe inside `synchronized` only because there is no other code path that sets `sftpClient` to null outside the synchronized block. Refactoring `ConnectionEntry` or adding concurrent writes to `sftpClient` will break this.
- Safe modification: Do not add writes to `sftpClient` outside `getSftpClient`. If `disconnect()` needs to null it out, it must also synchronize on `entry`.

**`RemoteTreeDropTarget` recreates its `CoroutineScope` on `reconnect()`:**
- Files: `src/main/kotlin/ro/faur/explorer/remote/ui/RemoteTreeDropTarget.kt`
- Why fragile: `reconnect()` cancels the old scope and creates a new one. If an upload coroutine is mid-flight when `reconnect()` is called, its cancellation exception is silently swallowed. No user feedback that the upload was aborted.
- Test coverage: Not tested.

## Scaling Limits

**FrecencyStore persists up to 10,000 entries in a single XML file:**
- Current capacity: 10,000 path entries (configured in `FrecencyStore.MAX_ENTRIES`)
- Limit: The XML is loaded fully into memory on IDE start and flushed entirely on every write. At 10k entries with long paths, this can be a multi-megabyte XML file.
- Scaling path: The eviction logic exists (`evictIfNeeded`) and works correctly; the concern is read/write performance as the file grows toward the limit.

**QuickOpen index hard-capped at 50,000 files:**
- Current capacity: 50,000 files (configurable via `maxIndexSize` setting)
- Limit: Users on very large monorepos will hit truncation frequently. The truncation warning is shown in the UI but there is no guidance on how to set a better root.
- Scaling path: Support per-directory scope overrides so users can pin the QuickOpen root to a subdirectory.

## Dependencies at Risk

**`@Deprecated` TreeSpeedSearch call:**
- Risk: `FileTreeComponent` uses `@Suppress("DEPRECATION")` on `TreeSpeedSearch(tree) {...}`. The deprecated constructor may be removed in a future IntelliJ SDK version.
- Impact: Compilation failure on SDK upgrade.
- Files: `src/main/kotlin/ro/faur/explorer/ui/FileTreeComponent.kt` line 122
- Migration plan: Migrate to `TreeSpeedSearch.installOn(tree, ...)` using the non-deprecated API when upgrading the IntelliJ platform version.

## Missing Critical Features

**No user-visible error when remote git is misconfigured:**
- Problem: When `getLog()` returns an empty list due to git not being on PATH (exit 127), the panel shows "no commits" with no indication that the git integration is broken. The fix (surfacing the error to the user) is identified in the test file but not yet implemented.
- Blocks: Trustworthy git panel usage on remote servers.

**`SecureTempFileManager` does not clean up on crash:**
- Problem: Cleanup is triggered by `TempFileCleanupListener` on IDE close, but not on IDE crash. Remote file contents linger in `~/.cache/system-explorer/temp` after a crash.
- Blocks: Secure handling of sensitive remote files.

## Test Coverage Gaps

**Swing drag-out path (FileTreeTransferHandler) has no tests:**
- What's not tested: The `createTransferable()` method in `FileTreeTransferHandler`, which builds the Transferable for dragging files out of the local tree to IntelliJ panels or the OS.
- Files: `src/main/kotlin/ro/faur/explorer/actions/FileTreeTransferHandler.kt`
- Risk: Regression in drag-out behavior goes undetected until manual testing.
- Priority: Medium

**`RemoteTreeDropTarget.reconnect()` race condition not tested:**
- What's not tested: Behavior when `reconnect()` is called while an upload coroutine is running.
- Files: `src/main/kotlin/ro/faur/explorer/remote/ui/RemoteTreeDropTarget.kt`
- Risk: Silent upload abortion with no user feedback.
- Priority: Medium

**`GitPanelComponent` reloadData behavior when backend returns null branch not tested:**
- What's not tested: The notification/fallback behavior in `GitPanelComponent` when `getCurrentBranch()` returns null (git unavailable).
- Files: `src/main/kotlin/ro/faur/explorer/gitpanel/ui/GitPanelComponent.kt`
- Risk: Silent failure in the git panel when git is not installed on the remote.
- Priority: High (the test `RemoteGitBackendSshTest` documents the bug but the UI behavior is untested)

**`SftpFileTreeModel` concurrent expansion race not tested:**
- What's not tested: Behavior when two `treeWillExpand` events fire simultaneously for the same node (rapid double-click or keyboard expansion).
- Files: `src/main/kotlin/ro/faur/explorer/remote/SftpFileTreeModel.kt`
- Risk: Duplicate children inserted into the tree model.
- Priority: Low (the placeholder guard provides some protection, but is not race-free under all JVM memory models)

---

*Concerns audit: 2026-02-28*
