# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

An IntelliJ Platform plugin (Kotlin, Gradle) scaffolded from the JetBrains plugin template. The
name signals the intended feature — completions for pnpm workspace **catalog:** protocol entries in
`package.json` — but none of that is implemented yet. Everything under `src/main/kotlin` is still
template demo code (`MyToolWindowFactory` shuffles a random number).

## Commands

All builds go through the Gradle wrapper; there is no npm/pnpm toolchain here despite the name.

```bash
./gradlew runIde          # launch a sandbox IDE with the plugin installed (logs: .intellijPlatform/sandbox/*/*/log/idea.log)
./gradlew build           # compile + assemble the plugin distribution
./gradlew check           # run tests (this is what the "Run Tests" run config maps to)
./gradlew verifyPlugin    # IntelliJ Plugin Verifier — compatibility check against target IDEs
./gradlew buildPlugin     # produce the distributable zip in build/distributions
```

Single test (no tests exist yet; `src/test` must be created first):

```bash
./gradlew test --tests "dev.wanjas.SomeTest"
./gradlew test --tests "dev.wanjas.SomeTest.someMethod"
```

The `.run/` directory holds equivalent IDE run configurations (Run IDE with Plugin / Run Tests /
Run Verifications).

## Architecture and conventions

- **Package root is `dev.wanjas`** (matches `group` in `gradle.properties` and the `<id>` prefix in
  `plugin.xml`), but sources sit directly in `src/main/kotlin` without the matching directory
  nesting. Keep new files consistent with whichever layout is in place.
- **`src/main/resources/META-INF/plugin.xml` is the wiring file.** Any new completion contributor,
  inspection, action, or service must be registered there as an `<extensions>` entry — a Kotlin
  class alone does nothing. The plugin already `<depends>` on `com.intellij.modules.json` (needed to
  touch `package.json` PSI) and `org.jetbrains.kotlin`, and declares K2 mode support.
- **Target platform is pinned in `build.gradle.kts`** via `intellijIdea("2025.3.5")` in the
  `intellijPlatform` dependencies block. Bundled plugin dependencies (`bundledPlugin(...)`) declared
  there must be mirrored by `<depends>` entries in `plugin.xml`, and vice versa.
- **All user-facing strings go through `MyMessageBundle`**, backed by
  `src/main/resources/messages/MyMessageBundle.properties` and declared as the `<resource-bundle>`
  in `plugin.xml`. The `@PropertyKey` annotation gives IDE-side key validation, so add the property
  before referencing the key.
- **Versions live in two places**: Gradle plugin versions in `settings.gradle.kts` `pluginManagement`
  (Kotlin, changelog, IntelliJ Platform Gradle Plugin), library versions in the
  `gradle/libs.versions.toml` version catalog.
- Gradle configuration cache and build cache are both enabled in `gradle.properties`; the Kotlin
  stdlib is deliberately *not* bundled (`kotlin.stdlib.default.dependency=false`) since the platform
  provides it.
- `CHANGELOG.md` is managed by the `org.jetbrains.changelog` plugin — edit the `[Unreleased]`
  section rather than hand-rolling release headers.

## Template leftovers worth fixing before shipping

`plugin.xml` still carries placeholder `<vendor>` (`YourCompany`) and `<description>` text, and the
plugin `<name>` is the raw project name. The demo tool window and `MyToolWindowFactory` should be
removed once real functionality lands.
