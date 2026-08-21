<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# pnpm Catalog Completions Changelog

## [Unreleased]

### Added

- npm version completion on catalog entries in `pnpm-workspace.yaml`, under both `catalog:` and
  `catalogs:`. Mirrors `package.json` dependency version completion: `^`/`~`/exact variants with the
  already-typed range kind first, `latest` at the top, every version once a prefix is typed or
  completion is invoked twice, and distribution tag names as values.
- Versions are read through the IDE's own npm registry service, so the cache and any `.npmrc`
  registry, scope and auth configuration are shared with `package.json` completion.
- The popup opens on its own after `<package>: ` and survives typing range punctuation.

