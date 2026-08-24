# pnpm Catalog Completions

An IntelliJ Platform plugin that adds npm version code completion to
[pnpm catalogs](https://pnpm.io/catalogs) in `pnpm-workspace.yaml`.

## What it does

Invoke code completion on the version of any entry under `catalog:` or `catalogs:` and you get the
list of versions published to the npm registry — the same completion the IDE already offers for
`package.json` dependencies:

```yaml
catalog:
  react: ^18.3.1     # <- completion here
catalogs:
  react17:
    react: ^17.0.2   # <- and here
```

- `^`/`~`/exact range variants, with the range kind you have already typed listed first
- `latest` at the top; the full version list once you type a prefix or invoke completion twice
- distribution tag names (`next`, `beta`, …) offered as values
- the popup opens on its own after `<package>: ` and survives typing range punctuation

Versions are read through the IDE's own npm registry service, so the metadata cache is shared with
`package.json` completion and any registry, scope or auth configuration in your `.npmrc` is honored.

## Requirements

The plugin depends on the bundled **JavaScript** plugin, which implies
`com.intellij.modules.ultimate`. It therefore runs in **IntelliJ IDEA Ultimate, WebStorm, PhpStorm,
RubyMine, PyCharm Professional, GoLand** and other commercial IDEs — but **not** IntelliJ IDEA
Community Edition.

Minimum IDE version: **2025.1** (build 251).

## Building from source

```bash
./gradlew runIde        # sandbox IDE with the plugin installed
./gradlew check         # run the tests
./gradlew verifyPlugin  # IntelliJ Plugin Verifier against every supported IDE
./gradlew buildPlugin   # distributable zip in build/distributions
```

The target IDE and the compatibility floor are set by `platformVersion` and `pluginSinceBuild` in
[`gradle.properties`](./gradle.properties). Keep them on the same release branch.

## Releasing

The first upload has to be done by hand at
<https://plugins.jetbrains.com/plugin/add> — `publishPlugin` can only update a listing that already
exists. After that:

```bash
# One-off: generate a signing certificate into the gitignored signing/ directory.
mkdir -p signing
openssl genpkey -aes-256-cbc -algorithm RSA -out signing/private.pem -pkeyopt rsa_keygen_bits:4096
openssl req -key signing/private.pem -new -x509 -days 365 -out signing/chain.crt

./gradlew patchChangelog          # moves [Unreleased] into a versioned section
export PRIVATE_KEY_PASSWORD=…     # the passphrase used above
export PUBLISH_TOKEN=…            # Marketplace profile -> My Tokens
./gradlew clean check verifyPlugin
./gradlew publishPlugin           # signs, then uploads
```

Bump `version` in `gradle.properties` before each release; it is stamped into the plugin descriptor.

## License

[MIT](./LICENSE)
