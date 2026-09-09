# Releasing

Farad'n follows [Semantic Versioning](https://semver.org/). `faradn-core`
publishes to Maven Central; the CLI and FFI ship as native binaries on GitHub
Releases. Pushing a `vX.Y.Z` tag runs
[`.github/workflows/release.yml`](.github/workflows/release.yml), which does both.

## One-time setup

- Verify the `net.nmoncho` namespace on <https://central.sonatype.com>.
- Add repository secrets: `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD`
  (a Central Portal user token) and `GPG_PRIVATE_KEY` / `MAVEN_GPG_PASSPHRASE`.

## Cutting a release

1. Make sure `main` is green: the `build`, `format`, and `native` CI jobs and the
   Security (Trivy) scan all pass.
2. Set the release version (drops `-SNAPSHOT`):
   ```console
   $ ./mvnw versions:set -DnewVersion=X.Y.Z -DprocessAllModules -DgenerateBackupPoms=false
   ```
3. Update `CHANGELOG.md`: rename `## [Unreleased]` to `## [X.Y.Z] - YYYY-MM-DD`,
   open a fresh empty `## [Unreleased]`, and add the compare/tag links at the bottom.
4. Regenerate the dependency attribution if dependencies changed (full plugin
   coordinates, because the short `license:` prefix belongs to the header plugin):
   ```console
   $ ./mvnw org.codehaus.mojo:license-maven-plugin:aggregate-add-third-party
   ```
   This rewrites `THIRD-PARTY.txt` at the repo root.
5. **For the 1.0.0 release only** (the SemVer stability commitment): remove the
   "Pre-1.0: the public API is not yet stable" lines from `README.md`,
   `CHANGELOG.md`, and `SECURITY.md`, and change `SECURITY.md`'s supported-versions
   note to a real 1.x policy.
6. Dry-run the publish locally:
   ```console
   $ ./mvnw -Prelease -DskipTests verify
   ```
7. Commit, then tag and push:
   ```console
   $ git commit -am "release: X.Y.Z"
   $ git tag vX.Y.Z
   $ git push origin main vX.Y.Z
   ```
8. `release.yml` publishes `faradn-core` to a Central **staging** deployment
   (`autoPublish=false`) and attaches the native binaries, checksums, `LICENSE`,
   `NOTICE`, and `THIRD-PARTY.txt`. Verify the staged deployment on the Central
   Portal, then **publish it manually**.
9. Bump `main` to the next development version:
   ```console
   $ ./mvnw versions:set -DnewVersion=X.Y.<Z+1>-SNAPSHOT -DprocessAllModules -DgenerateBackupPoms=false
   ```

## Notes

- `faradn --version` and the FFI `faradn_version` are filtered from
  `${project.version}` at build time, so they follow the POM automatically.
- `release.yml` sets `project.build.outputTimestamp` from the tag's commit date, so
  the published jars are reproducible.
- The native binaries are unsigned and un-notarized; keep the macOS Gatekeeper /
  Windows SmartScreen instructions (see the README) in the release notes.
- Only `faradn-core` (and the parent POM) go to Central; `faradn-cli` and
  `faradn-ffi` set `maven.deploy.skip`, and the deploy runs with `-pl .,faradn-core`.
