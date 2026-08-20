# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

An IntelliJ Platform plugin (Kotlin, Gradle) scaffolded from the JetBrains plugin template. 
The plugin is intended to provide completions for available versions for packes listed in catalogs in pnpm-workspace.yaml file. 
Version completion should work exactly the same way as in package.json files, but for pnpm-workspace.yaml files. 

This is implemented. `PnpmCatalogVersionCompletionContributor` is a port of the platform's own
`com.intellij.javascript.nodejs.packageJson.codeInsight.PackageJsonCompletionContributor` (closed
source, in the bundled JavaScript plugin) onto YAML PSI, so both paths behave alike and share one
npm metadata cache. When changing completion behaviour, decompile that class and match it rather
than inventing new rules — it is the specification.


## Commands

All builds go through the Gradle wrapper; there is no npm/pnpm toolchain here despite the name.

```bash
./gradlew runIde          # launch a sandbox IDE with the plugin installed (logs: .intellijPlatform/sandbox/*/*/log/idea.log)
./gradlew build           # compile + assemble the plugin distribution
./gradlew check           # run tests (this is what the "Run Tests" run config maps to)
./gradlew verifyPlugin    # IntelliJ Plugin Verifier — compatibility check against target IDEs
./gradlew buildPlugin     # produce the distributable zip in build/distributions
```

Single test:

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
  class alone does nothing. The plugin `<depends>` on `com.intellij.modules.platform`,
  `org.jetbrains.plugins.yaml` (YAML PSI) and `JavaScript` (the npm registry service).
- **The `JavaScript` dependency makes this an Ultimate-tier plugin.** That plugin declares
  `com.intellij.modules.ultimate`, so this one loads in IDEA Ultimate / WebStorm / PhpStorm / etc.,
  but not IDEA Community. Dropping that constraint would mean reimplementing npm metadata fetching
  plus `.npmrc` registry, scope and auth handling.
- **Most of the JS plugin's completion helpers are Kotlin `internal`** and unreachable from here —
  `PackageJsonCompletionUtil` and `PublicNpmRegistryServiceImpl` among them. Only
  `NpmRegistryService` and `AvailablePackageVersions` are public. Check visibility before planning to
  reuse anything from that plugin.
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

## Testing completion

`PnpmCatalogVersionCompletionTest` substitutes a fake `NpmRegistryService` project service
(`project.replaceService(...)`, from the Kotlin top-level `com.intellij.testFramework.replaceService`
— not `ServiceContainerUtil.replaceService`), so the suite runs offline. Add registry fixtures as
abbreviated-metadata JSON (`dist-tags` + `versions`) parsed by
`AvailablePackageVersions.parseFromPackageMetadata`.

Two traps when writing PSI-level tests against a catalog value:

- Completion never sees an empty value. The platform substitutes a dummy identifier at the caret
  first, so `react: <caret>` in a raw-PSI test resolves to the line break, which sits *outside* the
  `YAMLKeyValue`. Write `react: <caret>$DUMMY_IDENTIFIER_TRIMMED` to get the shape production sees.
- `PlainPrefixMatcher(prefix, true)` matches strictly, so an item is only offered when it starts with
  what is already typed. A test asserting `^18.3.1` must not place the caret after `^17.0`.
