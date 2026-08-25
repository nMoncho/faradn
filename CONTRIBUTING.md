# Contribution Guidelines

Thanks for your interest in Farad'n! This guide covers how to build, test, and
submit changes.

## Building and testing

The project is a Maven multi-module build (`faradn-core` + `faradn-cli`); use the
included wrapper so everyone builds with the same Maven version:

```console
$ ./mvnw verify                                  # build and run the full test suite
$ ./mvnw -pl faradn-core test                    # just the library's tests
```

Sources are auto-formatted on build by the `formatter-maven-plugin` (Eclipse
profile in `eclipse-formatter.xml`); CI enforces it with `formatter:validate`, so
run a build before pushing.

### Apple Silicon

usb4java 1.3.0 ships no `darwin-aarch64` native on Maven Central. Install a locally
built `libusb4java` jar once (see the README's *Building* section); the
`macos-aarch64` Maven profile wires it in.

### Native binary

```console
$ ./mvnw -pl faradn-cli -am -Pnative package     # requires GraalVM on JAVA_HOME
$ ./faradn-cli/target/faradn --help
```

If a native build fails on missing reflection/JNI metadata for a new dependency,
re-run the affected command under GraalVM's tracing agent and commit the merged
config:

```console
$ java -agentlib:native-image-agent=config-merge-dir=faradn-cli/src/main/resources/META-INF/native-image/io.nmoncho/faradn-cli \
    -jar faradn-cli/target/faradn-cli-*.jar <command>
```

## Tests that need a printer

Tests tagged `@Tag("hardware")` (`HardwarePrintTest`) talk to a real device and are
skipped by default. Enable them by pointing at your printer:

```console
$ ./mvnw -pl faradn-core test -Dfaradn.hardware=true -Dtest=HardwarePrintTest     # USB
$ ./mvnw -pl faradn-core test -Dfaradn.printer.host=192.168.1.50 -Dtest=HardwarePrintTest  # Ethernet
```

## Submitting an issue

There are templates for bug reports and feature requests. Please check the issue
hasn't been filed already. For security reports, see [SECURITY.md](SECURITY.md).

## Submitting a pull request

Whenever submitting a PR, please make sure everything builds and tests pass
locally. If you have a printer of your own, please attach screenshots or photos of
the print tests — especially when implementing a new feature or adding a device to
the capability matrix.
