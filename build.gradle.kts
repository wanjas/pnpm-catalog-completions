import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3.5")
        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:
        // YAML PSI (org.jetbrains.yaml.*) for reading pnpm-workspace.yaml.
        bundledPlugin("org.jetbrains.plugins.yaml")
        // NpmRegistryService/AvailablePackageVersions — the same npm metadata source that
        // package.json version completion uses. Ultimate-only, hence <depends>JavaScript</depends>.
        bundledPlugin("JavaScript")
    }
}
