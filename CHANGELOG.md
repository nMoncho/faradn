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
- **Cash drawer** - a `<cash-drawer>` element pulses the drawer-kick connector
  (`ESC p`), with a `pin` attribute selecting connector pin 2 (default) or 5.
- **Manual cut & feed** - `<cut>` (`mode="partial"`/`"full"`) and `<feed>`
  (`lines="n"`) elements cut the paper or feed blank lines at an explicit point,
  so a single job can hold several receipts. A trailing `<cut>` replaces the
  automatic end-of-job cut.
- **Module name** - the published `faradn-core` jar declares a stable JPMS module
  name, `net.nmoncho.faradn`, via `Automatic-Module-Name`, so a modular consumer
  can `requires net.nmoncho.faradn;`. jsoup and `javax.usb` are used internally
  only and are not exposed on the public API; the ESC/POS emission layer and the
  `internal.*` packages are marked internal (see their `package-info`).
- **USB API** - USB printers are opened with
  `UsbTransport.open(vendorId[, productId])` and enumerated with `Devices.list()`
  (returning neutral `UsbPrinter` values), so the public API no longer exposes
  `javax.usb` types.
- **FFI error contract** - the shared library's `faradn_render` returns a stable
  set of `FARADN_ERR_*` codes (invalid argument, unknown profile, render failure,
  out of memory) instead of a bare `-1`, and adds `faradn_last_error` (a
  per-thread message) and `faradn_version`. The transport-free render and
  error-classification logic is now unit-tested.

### Notes

- Pre-1.0: the public API is not yet stable and may change between releases.

[Unreleased]: https://github.com/nMoncho/faradn/commits/main
