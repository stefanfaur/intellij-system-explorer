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
