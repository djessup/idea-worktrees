plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
    id("org.jetbrains.kotlinx.kover") version "0.8.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
}

group = "au.id.deejay"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        create("IC", "2025.1.4.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add Git plugin for VCS integration
        bundledPlugin("Git4Idea")

        // Plugin Verifier
        pluginVerifier()
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    buildSearchableOptions = false // Allow dynamic reload

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes =
            """
            Initial version
            """.trimIndent()
    }

    pluginVerification {
        ides {
            // Verify against the IDE version we're building for
            ide("IC", "2025.1.4.1")
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

kover {
    reports {
        filters {
            includes {
                classes(
                    "au.id.deejay.ideaworktrees.services.*",
                    "au.id.deejay.ideaworktrees.model.*",
                )
            }
        }
    }
}

detekt {
    toolVersion = "1.23.6"
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
    baseline = file("$projectDir/config/detekt/baseline.xml")
}

tasks.named("check") {
    dependsOn("verifyPlugin")
    dependsOn("detekt")
}
