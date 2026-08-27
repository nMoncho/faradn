# Farad'n

Farad'n is an HTML-to-ESC/POS printing library for Java. The goal of this
project is to be a one stop shop for your printing needs when dealing with
ESC/POS (thermal receipt) printers: write your receipt as HTML, print it on
paper.

HTML is a widely used and understood format, and can easily be templated
with tools such as [Mustache](https://mustache.github.io/).

**Status.** The full pipeline works - HTML parsing, style resolution, the
renderer (text with word-wrap, images, barcodes, QR, tables), and transports
(USB and network) - plus a command line and HTTP server shipped as a GraalVM
native binary. Pre-1.0: the public API is not yet stable.

## Requirements

- Java 17+ (to use the library)
- GraalVM (only to build the native binary)

## Library usage

Parse an HTML document and send it to a printer:

```java
Document doc = Document.from("<h1>Receipt</h1><p>Total: <b>10,00</b></p>");

// Over USB, by the printer's USB vendor id (Epson is 0x04b8):
Printer.from(0x04b8).ifPresent(printer -> printer.print(doc));

// Over Ethernet (raw TCP, port 9100), or any other Transport:
try (Transport transport = new NetworkTransport("192.168.1.50")) {
  Printer.print(transport, doc, TmT88vProfile.INSTANCE);
}
```

You can also inspect the intermediate representation the renderer consumes - a
flat, reading-order list of blocks with fully resolved styles - or render
straight to ESC/POS bytes:

```java
List<Block> blocks = doc.blocks();
byte[] escpos = new EscPosRenderer(TmT88vProfile.INSTANCE).render(blocks);
```

## Command line

The `faradn` binary (a native executable, or `java -jar faradn-cli.jar`) has
three modes:

```console
$ faradn list                                     # connected USB printers
$ faradn print receipt.html --printer 0x04b8      # print over USB
$ faradn print receipt.html --host 192.168.1.50   # print over Ethernet (TCP 9100)
$ faradn print receipt.html --dry-run > job.bin   # render to ESC/POS bytes
$ faradn serve --port 8080 --host 192.168.1.50    # run the HTTP print server
```

`print` also accepts `--profile` and `--copies`.

## HTTP server

`faradn serve` starts a small, dependency-free HTTP server:

| Endpoint        | Description                                                   |
|-----------------|---------------------------------------------------------------|
| `POST /print`   | render the HTML request body and print it to the configured target |
| `GET /printers` | list connected USB printers                                   |
| `GET /health`   | liveness check                                                |

```console
$ curl -X POST --data '<h1>Hi</h1>' http://localhost:8080/print
{"status":"printed","bytes":123}
```

The server has no authentication - keep it off untrusted networks (see
[SECURITY.md](SECURITY.md)).

## Architecture

Farad'n never translates the DOM directly into printer bytes. Documents flow
through a pipeline with an explicit intermediate representation (IR):

```
HTML ─jsoup─▶ DOM ─BlockBuilder─▶ List<Block> (IR) ─EscPosRenderer─▶ ESC/POS ─Transport─▶ printer
```

- **`ComputedStyle`** resolves tags and inline CSS into properties an ESC/POS
  printer can realize: bold, underline, width/height multiples (1x–8x),
  alignment, inverted print. There is deliberately no italic - most ESC/POS
  printers cannot print it.
- **`Block`** is a sealed hierarchy shaped by the line-oriented nature of
  ESC/POS: `Paragraph`, `ImageBlock`, `Barcode`, `Rule`, `Feed`, `Cut`, and
  `Table`. Every `TextRun` carries its fully resolved style, so renderers only
  diff consecutive runs to emit minimal state changes.
- **`BlockBuilder`** walks the DOM with an explicit style stack, normalizes
  whitespace HTML-style, and flushes inline content at block boundaries.
- **`EscPosRenderer`** turns the IR into bytes: it diffs run styles, word-wraps
  to the profile's column budget, selects a code page (`ESC t`), rasterizes
  images to `GS v 0` with Floyd–Steinberg dithering, emits barcodes (`GS k`) and
  QR/PDF417 (`GS ( k`), and lays tables out on a character grid.
- **`Transport`** decouples byte generation from delivery: `UsbTransport`,
  `NetworkTransport` (TCP 9100) and `DumpTransport`, each able to read real-time
  status (`DLE EOT`) so a job can be refused before printing to an offline,
  covered, or out-of-paper printer.

This decoupling keeps HTML handling printer-agnostic and lets each stage be
tested in isolation: HTML → IR as plain object assertions, IR → bytes as
golden-byte tests.

### Modules

- **`faradn-core`** - the library (published to Maven Central).
- **`faradn-cli`** - the command line and HTTP server, shipped as a GraalVM
  native binary and attached to GitHub Releases.

## Supported HTML

**Tags**

| Markup                             | Effect                                                  |
|------------------------------------|---------------------------------------------------------|
| `<b>`, `<strong>`                  | bold                                                    |
| `<u>`                              | underline                                               |
| `<h1>`                             | bold, double width and height                           |
| `<h2>`                             | bold, double height                                     |
| `<h3>`                             | bold                                                    |
| `<center>`                         | centered                                                |
| `<p>`, `<div>`, headings, lists, … | paragraph (block) boundaries                            |
| `<br>`, `<hr>`                     | line break, horizontal rule                             |
| `<table>`, `<tr>`, `<td>`, `<th>`  | character-grid table (`<th>` is bold)                   |
| `<img>`                            | image (URL or Base64 `data:` URI; PNG, JPEG, BMP, WBMP) |
| `<em>`, `<i>`                      | ignored - ESC/POS printers have no italic               |

**Inline CSS**

- `font-weight` (`bold`, `bolder`, `600`–`900` ⇒ bold; `normal` switches it off)
- `text-decoration` / `text-decoration-line` (`underline`, `none`)
- `text-align` (`left`, `center`, `right`)

**Barcodes**

Either a custom element or a `bar-code` class with a symbology modifier:

```html
<bar-code symbology="code128">12345678</bar-code>
<bar-code symbology="qr">https://example.com</bar-code>
<div class="bar-code bar-code--upc-a">72527273073</div>
```

Supported symbologies: `code128`, `code39`, `code93`, `ean13`, `ean8`, `upca`,
`upce`, `itf`, `codabar` (1D), and `qr` / `pdf417` (2D).

**Text encoding.** Text is encoded for the profile's code page (TM-T88V defaults
to PC437); characters outside it fall back to `?`.

**Images.** PNG is decoded in pure Java, so it works everywhere, including the
GraalVM native binary. The JVM library (and `java -jar`) additionally reads JPEG,
BMP and WBMP via `javax.imageio`; those formats are **not** available in the native
binary, which has no AWT.

## Supported Devices

This project aims to support as many devices as possible, not only ESC/POS
(i.e. Epson) printers, but other thermal printers such as Brother, Zebra, among
others. If you've access to a printer that's not listed below, and would like
to contribute, please see [CONTRIBUTING.md](CONTRIBUTING.md).

### Capabilities per Device

| Brand    | Model           | Basic Styles       | Images             | Tables             | Barcodes           |
|----------|-----------------|--------------------|--------------------|--------------------|--------------------|
| Epson    | TM-T88V         | :white_check_mark: | :white_check_mark: | :white_check_mark: | :white_check_mark: |

**Basic Styles**

- Bold text
- Underlined text
- Left, center, and right text alignment
- Double-width / double-height text (headings)

## Building

```console
$ ./mvnw verify                                    # build and test everything
$ ./mvnw -pl faradn-cli -am -Pnative package       # build the native binary (needs GraalVM)
```

On **Apple Silicon**, usb4java 1.3.0 ships no `darwin-aarch64` native on Maven
Central. Build/obtain that `libusb4java` jar once and install it locally:

```console
$ mvn install:install-file -Dfile=libusb4java-1.3.0-darwin-aarch64.jar \
    -DgroupId=org.usb4java -DartifactId=libusb4java -Dversion=1.3.0 \
    -Dclassifier=darwin-aarch64 -Dpackaging=jar
```

The `macos-aarch64` Maven profile then wires it in automatically.

## Roadmap

- [x] HTML → IR: style resolution, paragraphs, images, barcodes
- [x] IR → ESC/POS renderer (diffing run styles, reusing the command layer)
- [x] Word wrapping based on printer profile (paper width × font size)
- [x] Character-grid layout for `<table>` and column layouts
- [x] Code page handling (Unicode → `ESC t` selection per printer)
- [x] Transports (USB and raw TCP 9100) with real-time status
- [x] CLI and HTTP server as a GraalVM native binary
- [ ] Publish `faradn-core` to Maven Central and native binaries to GitHub Releases
- [ ] Per-run code page switching and richer table cells
- [ ] Printer capability database (see [escpos-printer-db](https://github.com/receipt-print-hq/escpos-printer-db))
- [ ] Raster fallback for complex layouts

## Contribution Guidelines

Please see [CONTRIBUTING.md](CONTRIBUTING.md).

## Why Farad'n?

_Dune_ is one of my favourite books, there Farad'n is the grandson of Padishah
Emperor Shaddam IV, and he's the royal scribe by occupation.
