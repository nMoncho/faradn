# Contributing to Farad'n

Thanks for your interest in improving Farad'n. This document covers how to build
the project, the checks your change has to pass, and how to propose it.

## Getting started

You need JDK 17 or newer and Git. The Maven wrapper (`./mvnw`) pins the Maven
version, so you do not need Maven installed.

```console
$ git clone https://github.com/nMoncho/faradn.git
$ cd faradn
$ ./mvnw verify        # build and test the whole reactor
```

A GraalVM 17 distribution is needed only to build the native binaries
(`-Pnative` / `-Pnative-shared`); the library and its tests build on any JDK 17+.

On Apple Silicon, install the `darwin-aarch64` `libusb4java` native into your
local repository once. See the "Building" section of the [README](README.md).

## Project layout

- **`faradn-core`** - the library: HTML parsing, the intermediate representation,
  the ESC/POS renderer, transports, and the capability database. This is the only
  module published to Maven Central, so its public API is what consumers depend on.
- **`faradn-cli`** - the `faradn` command and the HTTP print server, shipped as a
  GraalVM native binary.
- **`faradn-ffi`** - a C ABI shared library exposing the renderer to other languages.

Packages under `net.nmoncho.faradn.internal.*` and `printer.escpos*` are internal
and may change without notice; see their `package-info.java`. Keep new public API
in `net.nmoncho.faradn`, `.document`, `.printer`, and `.transport`.

## Before you open a pull request

`./mvnw verify` runs the same gates as CI. In particular:

- **Formatting** - the Eclipse formatter (`eclipse-formatter.xml`) is enforced.
- **License headers** - every source file carries the SPDX header from
  `license-header.txt`.

Fix both automatically before committing:

```console
$ ./mvnw formatter:format license:format
```

CI also runs a Trivy dependency scan and builds the native CLI and shared library
to catch native-image reachability breakage. Add or update tests for the behavior
you change; JUnit 5 tests live under each module's `src/test/java`.

Hardware tests (`HardwarePrintTest`) talk to a real printer and are disabled
unless you point them at one:

```console
$ ./mvnw test -Dfaradn.hardware=true -Dtest=HardwarePrintTest               # USB
$ ./mvnw test -Dfaradn.printer.host=192.168.1.50 -Dtest=HardwarePrintTest   # TCP 9100
```

## Commit messages

Follow [Conventional Commits](https://www.conventionalcommits.org/) with a module
scope, matching the existing history:

```
feat(core): reject profiles with inconsistent width data
fix(cli): bind the print server to loopback by default
docs: document the release process
```

## Pull requests

1. Branch off `main`.
2. Keep the change focused; unrelated fixes belong in their own pull request.
3. Make sure `./mvnw verify` passes and the diff is formatted and header-stamped.
4. Describe what changed and why, and note any public-API or behavior change.

Public API changes get extra scrutiny while the project stabilizes toward 1.0; see
the pre-1.0 note in the [CHANGELOG](CHANGELOG.md).

## Reporting bugs and security issues

Open a normal issue for bugs and feature requests. For anything security-sensitive,
follow [SECURITY.md](SECURITY.md) instead of filing a public issue.

## License

Farad'n is MIT licensed. By contributing, you agree that your contributions are
licensed under the same [MIT License](LICENSE) that covers the project, and that
you have the right to submit them.
