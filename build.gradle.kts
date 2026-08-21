import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    // Every IDE from the 2025.1 floor upwards ships JBR 21. Without this pin the build silently
    // targets whatever JDK Gradle runs on, which produced unloadable Java 26 bytecode.
    jvmToolchain(21)
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Ultimate, because <depends>JavaScript</depends> implies com.intellij.modules.ultimate.
        // Note this must be intellijIdeaUltimate (ideaIU) and not intellijIdea — the latter maps to
        // the unified `com.jetbrains.intellij.idea:idea` artifact, which only exists from 2025.3.
        intellijIdeaUltimate(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
        // marketplace-zip-signer CLI, required by the signPlugin task.
        zipSigner()

        // Add plugin dependencies for compilation here:
        // YAML PSI (org.jetbrains.yaml.*) for reading pnpm-workspace.yaml.
        bundledPlugin("org.jetbrains.plugins.yaml")
        // NpmRegistryService/AvailablePackageVersions — the same npm metadata source that
        // package.json version completion uses. Ultimate-only, hence <depends>JavaScript</depends>.
        bundledPlugin("JavaScript")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 2025.1 is the lowest branch that compiles: NpmRegistryService.getInstance(Project)
            // does not exist in 2024.3 and earlier.
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            // Open-ended on purpose, so IDE upgrades do not disable the plugin.
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            // Ultimate-tier products only; the plugin cannot load anywhere without the JS plugin.
            select {
                types = listOf(
                    IntelliJPlatformType.IntellijIdeaUltimate,
                    IntelliJPlatformType.WebStorm,
                    IntelliJPlatformType.GoLand,
                    IntelliJPlatformType.RubyMine,
                    IntelliJPlatformType.PyCharmProfessional,
                    IntelliJPlatformType.PhpStorm,
                    IntelliJPlatformType.CLion,
                    IntelliJPlatformType.RustRover
                )
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = providers.gradleProperty("pluginSinceBuild")
            }
        }
    }

    // Certificate and key live in signing/, which is gitignored. Generate them with:
    //   openssl genpkey -aes-256-cbc -algorithm RSA -out signing/private.pem -pkeyopt rsa_keygen_bits:4096
    //   openssl req -key signing/private.pem -new -x509 -days 365 -out signing/chain.crt
    signing {
        certificateChainFile = layout.projectDirectory.file("signing/chain.crt")
        privateKeyFile = layout.projectDirectory.file("signing/private.pem")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

changelog {
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}
