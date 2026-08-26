# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims to
follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Renderer** - `EscPosRenderer` turns the HTML intermediate representation into
  ESC/POS bytes, diffing consecutive run styles to emit minimal state changes:
  bold, underline, size multiples, alignment, and inverted print.
- **Layout** - word wrapping to the printer's column budget, code-page selection
  (`ESC t`), images rasterized to `GS v 0` with Floyd–Steinberg dithering, 1D
  barcodes (`GS k`) and QR/PDF417 (`GS ( k`), and character-grid `<table>` layout.
- **Profiles & code pages** - `PrinterProfile` / `TmT88vProfile` and `CodePage`.
- **Transports** - `UsbTransport`, `NetworkTransport` (raw TCP 9100) and
  `DumpTransport`, with real-time status (`DLE EOT`) and a pre-flight readiness
  check that refuses to print to an offline / out-of-paper / cover-open printer.
- **CLI & server** - the `faradn` command (`list`, `print`, `serve`) and a
  dependency-free JDK HTTP print server, shipped as a GraalVM native binary.

### Notes

- Pre-1.0: the public API is not yet stable and may change between releases.

[Unreleased]: https://github.com/nMoncho/faradn/commits/main
