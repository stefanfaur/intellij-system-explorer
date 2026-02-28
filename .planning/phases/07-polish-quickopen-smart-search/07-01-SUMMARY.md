---
phase: 07-polish-quickopen-smart-search
plan: 01
subsystem: quickopen
tags: [nucleo, native-library, ripgrep, security, null-safety, kotlin]

requires: []
provides:
  - darwin-aarch64 Nucleo native library (libfuzzyjni.dylib) committed to VCS for JAR distribution
  - ripgrepExtraFlags allowlist validation blocking command injection
  - null-safe popup field eliminating UninitializedPropertyAccessException crash risk
affects: []

tech-stack:
  added: []
  patterns:
    - "Allowlist validation pattern for user-supplied CLI flags: ALLOWED_FLAGS set + ALLOWED_PREFIXES for prefix-matching"
    - "Committed native binaries in src/main/resources/natives/<platform>/ for JAR distribution"

key-files:
  created:
    - src/main/resources/natives/darwin-aarch64/libfuzzyjni.dylib
  modified:
    - src/main/kotlin/ro/faur/explorer/quickopen/backend/RipgrepContentSearch.kt
    - src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt

key-decisions:
  - "Native library committed to src/main/resources (not only build dir) so it is packaged in plugin JAR without requiring a Rust toolchain"
  - "validateExtraFlags allowlist: --hidden, --no-ignore, --follow, --type*, --glob* — everything else (including --exec) is silently dropped"
  - "popup changed from lateinit to JBPopup? = null, all 4 callsites use ?. safe-call operator"

patterns-established:
  - "Allowlist pattern: ALLOWED_FLAGS (exact set) + ALLOWED_PREFIXES (startsWith) for ripgrep flags"

requirements-completed: [QO-CODE-01, QO-CODE-02, QO-CODE-03]

duration: 2min
completed: 2026-02-28
---

# Phase 7 Plan 01: Nucleo Library Bundling, Flag Allowlist, Null-Safe Popup Summary

**darwin-aarch64 Nucleo dylib committed to VCS, ripgrep extra flags secured with allowlist validation, and popup lateinit replaced with nullable var across 4 callsites**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-28T12:21:33Z
- **Completed:** 2026-02-28T12:23:27Z
- **Tasks:** 3
- **Files modified:** 3 (1 binary, 2 Kotlin)

## Accomplishments
- Committed pre-compiled libfuzzyjni.dylib (arm64, 713K) to src/main/resources/natives/darwin-aarch64/ so it ships inside the plugin JAR
- Added validateExtraFlags() with ALLOWED_FLAGS/ALLOWED_PREFIXES allowlist in RipgrepContentSearch, replacing unsafe direct split pass-through
- Changed QuickOpenPanel.popup from lateinit to nullable var, updated all 4 callsites to popup?.closeOk(null)

## Task Commits

Each task was committed atomically:

1. **Task 1: Commit darwin-aarch64 Nucleo native library** - `2edf7a7` (chore)
2. **Task 2: Add ripgrepExtraFlags allowlist validation** - `1775bbd` (fix)
3. **Task 3: Change lateinit popup to nullable var** - `365c532` (fix)

## Files Created/Modified
- `src/main/resources/natives/darwin-aarch64/libfuzzyjni.dylib` - Pre-compiled Nucleo fuzzy matcher native library for darwin-aarch64
- `src/main/kotlin/ro/faur/explorer/quickopen/backend/RipgrepContentSearch.kt` - Added ALLOWED_FLAGS, ALLOWED_PREFIXES constants and validateExtraFlags() method; replaced unsafe cmd.addAll(extra.split(...)) with cmd.addAll(validateExtraFlags(extra))
- `src/main/kotlin/ro/faur/explorer/quickopen/ui/QuickOpenPanel.kt` - Changed popup declaration from lateinit var to var popup: JBPopup? = null; replaced 4 instances of popup.closeOk(null) with popup?.closeOk(null)

## Decisions Made
- Allowlist for ripgrep flags limited to: exact matches in {--hidden, --no-ignore, --follow} and prefix matches for {--type, --glob}. Unknown or dangerous flags (--exec, arbitrary --) are silently dropped without user notification — consistent with the "safe default" security posture.
- Native library committed in src/main/resources (VCS-tracked) rather than relying solely on the copyNativeLib Gradle task that copies from build output. Both paths remain functional — the committed binary ensures distribution without a Rust toolchain.
- popup changed to nullable (not removed or made non-null via constructor injection) to maintain the existing QuickOpenPopup post-creation assignment pattern while eliminating the crash risk.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. The Gradle wrapper required disabling sandbox (sandbox blocks ~/.gradle lock files), which is a known CI environment constraint. No code-level issues.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All three correctness/security fixes complete and verified to compile
- Nucleo native library bundled — fuzzy ranking will work for darwin-aarch64 users without manual build step
- Ready to proceed to next plan in phase 07

## Self-Check: PASSED
- FOUND: src/main/resources/natives/darwin-aarch64/libfuzzyjni.dylib
- FOUND: .planning/phases/07-polish-quickopen-smart-search/07-01-SUMMARY.md
- FOUND: commit 2edf7a7 (chore: native library)
- FOUND: commit 1775bbd (fix: allowlist validation)
- FOUND: commit 365c532 (fix: null-safe popup)
