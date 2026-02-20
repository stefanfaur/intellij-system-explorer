import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) })

        pluginVerifier()
        zipSigner()

        testFramework(TestFrameworkType.Platform)
    }

    testImplementation(libs.junit)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("junit:junit:4.13.2") // needed for BasePlatformTestCase (JUnit 3 based)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4") // bridges JUnit 3/4 tests to JUnit Platform

    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")

    testImplementation("com.intellij.remoterobot:remote-robot:0.11.23")
    testImplementation("com.intellij.remoterobot:remote-fixtures:0.11.23")
}

kotlin {
    jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())

    // All phases are implemented — no test exclusions needed.
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            // Verify against ALL compatible IDE versions
            create(providers.gradleProperty("platformType").get(), providers.gradleProperty("platformVersion").get()) // 2025.1
            create(providers.gradleProperty("platformType").get(), "2025.2")
            create(providers.gradleProperty("platformType").get(), "2025.3")
        }
    }
}

intellijPlatformTesting {
    runIde {
        register("testUi") {
            task {
                jvmArgumentProviders.add(CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                })
            }
        }
    }
}

// ── Nucleo JNI native library build ──────────────────────────────────────
val rustFuzzyDir = file("rust-fuzzy")
val skipCargo = project.findProperty("skipCargo")?.toString()?.toBoolean() == true

/** Resolve the cargo binary, searching well-known install locations if not on PATH. */
fun resolveCargoExecutable(): String? {
    // Well-known locations: Homebrew (Apple Silicon / Intel), rustup default, Linux
    val candidates = listOf(
        "cargo",
        "/opt/homebrew/bin/cargo",
        "/usr/local/bin/cargo",
        "${System.getProperty("user.home")}/.cargo/bin/cargo",
    )
    for (candidate in candidates) {
        try {
            val ok = ProcessBuilder(candidate, "--version")
                .redirectErrorStream(true).start().waitFor() == 0
            if (ok) return candidate
        } catch (_: Exception) { /* try next */ }
    }
    return null
}

val cargoBin = if (!skipCargo && rustFuzzyDir.resolve("Cargo.toml").exists()) resolveCargoExecutable() else null

val cargoRelease by tasks.registering(Exec::class) {
    group = "build"
    description = "Build nucleo JNI native library (current platform only)"
    workingDir = rustFuzzyDir
    commandLine(cargoBin ?: "cargo", "build", "--release")
    isIgnoreExitValue = true   // graceful: non-zero exit → native absent → fallback ranker
    enabled = cargoBin != null
}

val copyNativeLib by tasks.registering(Copy::class) {
    dependsOn(cargoRelease)
    from(rustFuzzyDir.resolve("target/release")) {
        include("*.so", "*.dylib", "*.dll")
    }
    into(layout.buildDirectory.dir("resources/main/natives/${detectHostPlatform()}"))
    enabled = !skipCargo
}

tasks.processResources { dependsOn(copyNativeLib) }

fun detectHostPlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm")) -> "darwin-aarch64"
        os.contains("mac") -> "darwin-x86_64"
        os.contains("linux") -> "linux-x86_64"
        else -> "win32-x86_64"
    }
}

tasks {
    test {
        useJUnitPlatform()
        val includeIdeTests = project.findProperty("includeIdeTests")?.toString()?.toBoolean() == true
        // Default: keep :test headless-safe.
        // Opt-in for light/heavy suites with -PincludeIdeTests=true.
        if (!includeIdeTests) {
            exclude("ro/faur/explorer/light/**")
            exclude("ro/faur/explorer/heavy/**")
        }
        exclude("ro/faur/explorer/ui/**")
    }

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
