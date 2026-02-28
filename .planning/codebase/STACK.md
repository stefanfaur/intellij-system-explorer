# Technology Stack

**Analysis Date:** 2026-02-28

## Languages

**Primary:**
- Kotlin 2.1.0 - All plugin source code (`src/main/kotlin/`, `src/test/kotlin/`)

**Secondary:**
- Rust (edition 2021) - Native fuzzy-matching JNI library (`rust-fuzzy/`)
- Java - Used implicitly via JVM interop; Gradle build scripts include `id("java")`

## Runtime

**Environment:**
- JVM 21 (Temurin distribution in CI)
- Target: IntelliJ Platform IC 2025.1 (`platformVersion = 2025.1`)

**Package Manager:**
- Gradle 8.13 (via Gradle Wrapper)
- Lockfile: `gradle/wrapper/gradle-wrapper.properties` present; no dependency lockfile enforced
- Cargo (Rust) for the native fuzzy library sub-project (`rust-fuzzy/Cargo.toml`)

## Frameworks

**Core:**
- IntelliJ Platform SDK (IC 2025.1) - Plugin host; all UI, actions, services, VFS, and settings APIs come from here
- Apache MINA SSHD 2.17.1 (`sshd-core`, `sshd-sftp`, `sshd-common`) - SSH/SFTP remote browser
- kotlinx-coroutines (bundled with IntelliJ; `kotlinx-coroutines-test:1.10.1` in test scope) - Async file enumeration and content search in Quick Open

**Testing:**
- JUnit Jupiter 5.11.4 - Unit test runner (`libs.junit`)
- JUnit 4.13.2 + `junit-vintage-engine:5.11.4` - Required for `BasePlatformTestCase` (IntelliJ light/heavy IDE tests, which are JUnit 3-based)
- Mockito 5.14.2 + mockito-kotlin 5.4.0 - Mocking framework for unit tests
- IntelliJ Remote Robot 0.11.23 (`remote-robot`, `remote-fixtures`) - UI/end-to-end testing against a running IDE

**Build/Dev:**
- `org.jetbrains.intellij.platform` Gradle plugin 2.11.0 - IntelliJ plugin build, sign, verify, publish
- `org.gradle.toolchains.foojay-resolver-convention:0.9.0` - JDK toolchain resolution
- Cargo (Rust) invoked via Gradle `Exec` task (`cargoRelease`) to build the native JNI library

## Key Dependencies

**Critical:**
- `org.apache.sshd:sshd-core:2.17.1` - SSH client sessions for remote SFTP connections
- `org.apache.sshd:sshd-sftp:2.17.1` - SFTP client operations (list, read, write, delete) over SSH
- `org.apache.sshd:sshd-common:2.17.1` - Shared SSHD utilities (key loading, security)
- Nucleo JNI native library (`rust-fuzzy/`, output: `libfuzzyjni.dylib/.so/.dll`) - High-performance fuzzy scoring via `nucleo-matcher:0.3` crate; loaded at runtime via `NucleoNative`
- `com.google.gson.JsonParser` (bundled with IntelliJ) - Parses `rg --json` output in `RipgrepContentSearch.kt`
- `jni:0.22` (Rust crate) - JNI bridge between Kotlin and the Rust fuzzy scorer

**Infrastructure:**
- `org.jetbrains.plugins.terminal` (bundled IntelliJ plugin) - Declared as a plugin dependency; used for SSH terminal integration (`SshTerminalAction.kt`)
- `junit-platform-launcher` - JUnit 5 platform launcher (test runtime only)

## Configuration

**Environment:**
- Plugin metadata and build parameters in `gradle.properties`:
  - `pluginGroup`, `pluginName`, `pluginVersion`
  - `platformType = IC`, `platformVersion = 2025.1`
  - `javaVersion = 21`, `kotlinVersion = 2.1.0`, `gradleVersion = 8.13`
  - `platformBundledPlugins = org.jetbrains.plugins.terminal`
- Release signing and publishing use environment variables (not in source):
  - `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD` (signing)
  - `PUBLISH_TOKEN` (JetBrains Marketplace publish)

**Build:**
- `build.gradle.kts` - Primary build file with dependency declarations, plugin configuration, and native build tasks
- `gradle/libs.versions.toml` - Version catalog for Kotlin plugin and IntelliJ Platform plugin
- `settings.gradle.kts` - Root project name (`intellij-explorer`) and plugin management repos
- Native lib build is controlled by Gradle property `skipCargo=true` to bypass Cargo during CI snapshot packaging

## Platform Requirements

**Development:**
- JDK 21
- Rust toolchain (stable) + Cargo — required to build the native JNI fuzzy lib locally
- `cargo` discoverable at well-known paths (`/opt/homebrew/bin/cargo`, `~/.cargo/bin/cargo`, or on PATH)
- `rg` (ripgrep) on PATH — optional; used at runtime for file enumeration and content search; falls back to VFS enumerator if absent

**Production:**
- Deployed as an IntelliJ plugin (`.zip`) to the JetBrains Marketplace
- Compatible with IntelliJ IDEA Community (IC) builds 251–253.x (`pluginSinceBuild = 251`, `pluginUntilBuild = 253.*`)
- Native libraries for all four platforms are bundled in `src/main/resources/natives/{platform}/`:
  - `darwin-aarch64` (Apple Silicon)
  - `darwin-x86_64` (Intel Mac)
  - `linux-x86_64`
  - `win32-x86_64`

---

*Stack analysis: 2026-02-28*
